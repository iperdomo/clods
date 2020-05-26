(ns com.eldrix.clods.postcode
  (:require
    [clojure.string :as s]
    [clojure.java.io :as io]
    [next.jdbc :as jdbc]
    [clojure.data.json :as json]
    [clojure.data.csv :as csv])

  (:import
    (java.io InputStreamReader)))


;; The NHS Postcode directory ("NHSPD") lists all current and terminated postcodes in the UK
;; and relates them to a range of current statutory administrative, electoral, health and other
;; geographies.
;;
;; Unfortunately, it is not possible to automatically download these data from a machine-readable
;; canonical resource, but the download is available manually.
;;
;; The February 2020 release is available at:
;; https://geoportal.statistics.gov.uk/datasets/nhs-postcode-directory-uk-full-february-2020
;;
;;

(def field-names ["PCD2" "PCDS" "DOINTR" "DOTERM" "OSEAST100M"
                  "OSNRTH100M" "OSCTY" "ODSLAUA" "OSLAUA" "OSWARD"
                  "USERTYPE" "OSGRDIND" "CTRY" "OSHLTHAU" "RGN"
                  "OLDHA" "NHSER" "CCG" "PSED" "CENED"
                  "EDIND" "WARD98" "OA01" "NHSRLO" "HRO"
                  "LSOA01" "UR01IND" "MSOA01" "CANNET" "SCN"
                  "OSHAPREV" "OLDPCT" "OLDHRO" "PCON" "CANREG"
                  "PCT" "OSEAST1M" "OSNRTH1M" "OA11" "LSOA11"
                  "MSOA11" "CALNCV" "STP"])

(defn normalize
  "Normalizes a postcode into uppercase 8-characters with left-aligned outward code and right-aligned inward code
  returning the original if normalization not possible"
  [pc]
  (let [codes (s/split pc #"\s+")] (if (= 2 (count codes)) (apply #(format "%-5s %3s" %1 %2) codes) pc)))

(defn egif
  "Normalizes a postcode into uppercase with outward code and inward codes separated by a single space"
  [pc]
  (s/replace pc #"\s+" " "))

(defn import-postcodes
  "Import/update postcode data (NHSPD e.g. nhg20feb.csv) to the datasource (ds) specified"
  [f ds]
  (let [btchs (->> f
                  (io/input-stream)
                  (InputStreamReader.)
                  (csv/read-csv)
                  (map #(zipmap field-names %))
                  (map #(vector (get % "PCDS") (get % "PCD2" ) (clojure.data.json/write-str %)))
                  (partition-all 1000))]
    (with-open [con (jdbc/get-connection ds)
                ps (jdbc/prepare con ["insert into postcodes (PCD2,PCDS,DATA) values (?,?,?::jsonb) on conflict (PCD2) do update set PCDS = EXCLUDED.PCDS, DATA=EXCLUDED.DATA"])]
      (run! #(next.jdbc.prepare/execute-batch! ps %) btchs))))

(comment

  ;; this is the Feb 2020 release file (928mb)
  (def filename "/Users/mark/Downloads/NHSPD_FEB_2020_UK_FULL/Data/nhg20feb.csv")

  (def db {:dbtype "postgresql" :dbname "ods"})
  (def ds (jdbc/get-datasource db))

  (import-postcodes filename ds)



  (def reader (InputStreamReader. (io/input-stream filename)))
  (def pc (csv/read-csv reader))

  (def btchs (->> filename
                  (io/input-stream)
                  (InputStreamReader.)
                  (csv/read-csv)
                  (map #(zipmap field-names %))
                  (map #(vector (get % "PCDS") (get % "PCD2" ) (clojure.data.json/write-str %)))
                  (partition-all 1000)))

  (def db {:dbtype "postgresql" :dbname "ods"})
  (def ds (jdbc/get-datasource db))

  (with-open [con (jdbc/get-connection ds)
              ps (jdbc/prepare con ["insert into postcodes (PCD2,PCDS,DATA) values (?,?,?::jsonb) on conflict (PCD2) do update set PCDS = EXCLUDED.PCDS, DATA=EXCLUDED.DATA"])]
    (run! #(next.jdbc.prepare/execute-batch! ps %) btchs)
    )
  )

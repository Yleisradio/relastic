[![Build Status](https://travis-ci.org/Yleisradio/relastic.svg?branch=master)](https://travis-ci.org/Yleisradio/relastic)

# relastic

Tool for reindexing ElasticSearch documents after mapping changes.

## Usage 

### Command line usage

    $ java -jar relastic-0.1.0-standalone.jar --host HOST \
                                              --port PORT \
                                              --from-index twitter_v1 \
                                              --to-index twitter_v2 \
                                              --alias twitter_read \
                                              --new-alias twitter_write \
                                              --mappings document-mappings.json \
                                              --settings document-settings.json

Executing this will:

1. create a new index `twitter_v2` with mappings and settings from specified files
2. add `twitter_write` alias to `twitter_v2` immediately
3. copies all documents from `twitter_v1` to `twitter_v2`
4. add `twitter_read` alias to `twitter_v2` and remove it from `twitter_v1` after all documents have been copied

You can omit `--from-index`, `--alias`, `--new-alias`, `--mappings` and `--settings`.

`--host` and `--port` default to `localhost` and `9300` (tranport client is used, not HTTP)

**NOTE**: After reindexing has started, any new documents written to `from-index` won't be copied
to the new index. To make sure all documents are copied, use `to-index` as the index with all
ElasticSearch write operations.

### Clojure usage

Include

    [yleisradio/relastic "0.1.0"]

to your `project.clj`. Then:

    (require '[relastic.core :as relastic])
    (relastic/update-mappings conn :from-index "twitter_v1"
                                   :to-index "twitter_v2"
                                   :alias "twitter_read"
                                   :new-alias "twitter_write"
                                   :mappings {:tweet {:properties {:user {:type "string"
                                                                          :index "not_analyzed"}}}}
                                   :settings {:index {:refresh_interval "20s"}})

`conn` should be a ElasticSearch `TransportClient` (use `clojurewerkz.elastisch.native/connect` to receive one)

Otherwise, same options apply as when used from command line.

## License

Copyright © 2015 Yleisradio Oy

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

[![Build Status](https://travis-ci.org/Yleisradio/relastic.svg?branch=master)](https://travis-ci.org/Yleisradio/relastic)

# relastic

Tool for reindexing ElasticSearch documents after mapping changes.

## Usage 

### Command line usage

    $ java -jar relastic-0.3.0-standalone.jar --host HOST \
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

    [yleisradio/relastic "0.3.0"]

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

#### Migrating documents

If you want to modify documents before they are indexed to `:to-index`, you can do so 
by passing an optional `:migration-fn` function. This is useful in scenarios where you've
removed a field from your schema and also want it removed from the documents, or when you've
added a field and want to fill in a default value.

    (defn- migrate-document [{:keys [_type] :as doc}]
      (if (= _type "tweet")
        (assoc-in doc [:_source :author] "anonymous")
        doc))

    (relastic/update-mappings conn :from-index "twitter_v1"
                                   :to-index "twitter_v2"
                                   :mappings new-mappings
                                   :settings new-settings
                                   :migration-fn migrate-document)

The example above would add `:author "anonymous"` field to all `tweet` documents, but leave other
document types unmodified.

The `:migration-fn` takes only one argument, the elasticsearch document. The available fields
are the same that would be available through ElasticSearch's REST API. For migration
purposes, the most important fields here are:

* `:_type` - original document type
* `:_source` - original document as map

The return value should be a map with the same fields that were in the original document. Usually
you'll only want to alter the `:_source` field, but if you want, you can also rename the type to
something else. This would cause the documents to appear under different type name in the new index.

You should not reconstruct the document map by yourself. Instead, use `assoc-in`, `update-in`
or something similar to only do the necessary modifications.

At the moment, it's not possible to specify `:migration-fn` from command line.

## License

Copyright © 2015 Yleisradio Oy

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.


#!/bin/bash

schema_id="2"
messages='{ "resourceType": "DATASET", "fdkId": "6467f35c-f246-48f5-956c-616b949e52e6", "timestamp": 1647698566000, "data": "ADD VALID JSON HERE" }
          { "resourceType": "DATASET", "fdkId": "28df0d6b-8e75-4136-84f3-f3f3c65fbd7c", "timestamp": 1647698566000, "data": "ADD VALID JSON HERE" }
          { "resourceType": "DATASET", "fdkId": "2680f16a-22ed-4239-ae9d-259791403fee", "timestamp": 1647698566000, "data": "ADD VALID JSON HERE" }'

docker-compose exec schema-registry bash -c "echo '$messages'|kafka-avro-console-producer --bootstrap-server kafka:29092 --topic rdf-parse-events --property value.schema.id=${schema_id}"

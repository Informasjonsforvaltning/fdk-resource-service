db = db.getSiblingDB('fdkResourceService');
db.createCollection('datasets', {
    validator: {
       $jsonSchema: {
          bsonType: "object",
          title: "ID Validation",
          required: [ "_id" ],
          properties: {
             _id: {
                bsonType: "string",
                minLength: 1,
                description: "'_id' must have length over 0"
             }
          }
       }
    }
});
db.datasets.insert(
    {
        "_id": "123",
        "removed": false,
        "resource": {
            "id": "123",
            "type": "datasets",
            "uri": "https://datasets.digdir.no/321",
            "identifier": "321",
            "title": {
                "nb": "dataset nb",
                "nn": "dataset nn",
                "en": "dataset en"
            },
            "description": {
                "nb": "dataset desc nb",
                "nn": "dataset desc nn",
                "en": "dataset desc en"
            }
        }
    }
);
db.datasets.insert(
    {
        "_id": "111",
        "removed": false,
        "resource": {
            "id": "111",
            "type": "datasets",
            "uri": "https://datasets.digdir.no/654",
            "identifier": "654",
            "title": {
                "nb": "dataset nb",
                "nn": "dataset nn",
                "en": "dataset en"
            },
            "description": {
                "nb": "dataset desc nb",
                "nn": "dataset desc nn",
                "en": "dataset desc en"
            }
        }
    }
);
db.datasets.insert(
    {
        "_id": "222",
        "removed": true,
        "resource": {
            "id": "222",
            "type": "datasets",
            "uri": "https://datasets.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "removed dataset nb",
                "nn": "removed dataset nn",
                "en": "removed dataset en"
            },
            "description": {
                "nb": "removed dataset desc nb",
                "nn": "removed dataset desc nn",
                "en": "removed dataset desc en"
            }
        }
    }
);

db.createCollection('concepts', {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "ID Validation",
            required: [ "_id" ],
            properties: {
                _id: {
                    bsonType: "string",
                    minLength: 1,
                    description: "'_id' must have length over 0"
                }
            }
        }
    }
});
db.concepts.insert(
    {
        "_id": "123",
        "removed": false,
        "resource": {
            "id": "123",
            "type": "concepts",
            "uri": "https://concepts.digdir.no/321",
            "identifier": "321",
            "title": {
                "nb": "concept nb",
                "nn": "concept nn",
                "en": "concept en"
            },
            "description": {
                "nb": "concept desc nb",
                "nn": "concept desc nn",
                "en": "concept desc en"
            }
        }
    }
);
db.concepts.insert(
    {
        "_id": "111",
        "removed": false,
        "resource": {
            "id": "111",
            "type": "concepts",
            "uri": "https://concepts.digdir.no/654",
            "identifier": "654",
            "title": {
                "nb": "concept nb",
                "nn": "concept nn",
                "en": "concept en"
            },
            "description": {
                "nb": "concept desc nb",
                "nn": "concept desc nn",
                "en": "concept desc en"
            }
        }
    }
);
db.concepts.insert(
    {
        "_id": "222",
        "removed": true,
        "resource": {
            "id": "222",
            "type": "concepts",
            "uri": "https://concepts.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "removed concept nb",
                "nn": "removed concept nn",
                "en": "removed concept en"
            },
            "description": {
                "nb": "removed concept desc nb",
                "nn": "removed concept desc nn",
                "en": "removed concept desc en"
            }
        }
    }
);

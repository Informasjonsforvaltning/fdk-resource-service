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

db.createCollection('services', {
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
db.services.insert(
    {
        "_id": "123",
        "removed": false,
        "resource": {
            "id": "123",
            "type": "services",
            "uri": "https://services.digdir.no/321",
            "identifier": "321",
            "title": {
                "nb": "service nb",
                "nn": "service nn",
                "en": "service en"
            },
            "description": {
                "nb": "service desc nb",
                "nn": "service desc nn",
                "en": "service desc en"
            }
        }
    }
);
db.services.insert(
    {
        "_id": "111",
        "removed": false,
        "resource": {
            "id": "111",
            "type": "services",
            "uri": "https://services.digdir.no/654",
            "identifier": "654",
            "title": {
                "nb": "service nb",
                "nn": "service nn",
                "en": "service en"
            },
            "description": {
                "nb": "service desc nb",
                "nn": "service desc nn",
                "en": "service desc en"
            }
        }
    }
);
db.services.insert(
    {
        "_id": "222",
        "removed": true,
        "resource": {
            "id": "222",
            "type": "services",
            "uri": "https://services.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "removed service nb",
                "nn": "removed service nn",
                "en": "removed service en"
            },
            "description": {
                "nb": "removed service desc nb",
                "nn": "removed service desc nn",
                "en": "removed service desc en"
            }
        }
    }
);

db.createCollection('events', {
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
db.events.insert(
    {
        "_id": "123",
        "removed": false,
        "resource": {
            "id": "123",
            "type": "events",
            "uri": "https://events.digdir.no/321",
            "identifier": "321",
            "title": {
                "nb": "event nb",
                "nn": "event nn",
                "en": "event en"
            },
            "description": {
                "nb": "event desc nb",
                "nn": "event desc nn",
                "en": "event desc en"
            }
        }
    }
);
db.events.insert(
    {
        "_id": "111",
        "removed": false,
        "resource": {
            "id": "111",
            "type": "events",
            "uri": "https://events.digdir.no/654",
            "identifier": "654",
            "title": {
                "nb": "event nb",
                "nn": "event nn",
                "en": "event en"
            },
            "description": {
                "nb": "event desc nb",
                "nn": "event desc nn",
                "en": "event desc en"
            }
        }
    }
);
db.events.insert(
    {
        "_id": "222",
        "removed": true,
        "resource": {
            "id": "222",
            "type": "events",
            "uri": "https://events.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "removed event nb",
                "nn": "removed event nn",
                "en": "removed event en"
            },
            "description": {
                "nb": "removed event desc nb",
                "nn": "removed event desc nn",
                "en": "removed event desc en"
            }
        }
    }
);

db.createCollection('informationModels', {
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
db.informationModels.insert(
    {
        "_id": "123",
        "removed": false,
        "resource": {
            "id": "123",
            "type": "informationModels",
            "uri": "https://information-models.digdir.no/321",
            "identifier": "321",
            "title": {
                "nb": "information model nb",
                "nn": "information model nn",
                "en": "information model en"
            },
            "description": {
                "nb": "information model desc nb",
                "nn": "information model desc nn",
                "en": "information model desc en"
            }
        }
    }
);
db.informationModels.insert(
    {
        "_id": "111",
        "removed": false,
        "resource": {
            "id": "111",
            "type": "informationModels",
            "uri": "https://information-models.digdir.no/654",
            "identifier": "654",
            "title": {
                "nb": "information model nb",
                "nn": "information model nn",
                "en": "information model en"
            },
            "description": {
                "nb": "information model desc nb",
                "nn": "information model desc nn",
                "en": "information model desc en"
            }
        }
    }
);
db.informationModels.insert(
    {
        "_id": "222",
        "removed": true,
        "resource": {
            "id": "222",
            "type": "informationModels",
            "uri": "https://information-models.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "removed information model nb",
                "nn": "removed information model nn",
                "en": "removed information model en"
            },
            "description": {
                "nb": "removed information model desc nb",
                "nn": "removed information model desc nn",
                "en": "removed information model desc en"
            }
        }
    }
);

db.createCollection('dataServices', {
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
db.dataServices.insert(
    {
        "_id": "123",
        "removed": false,
        "resource": {
            "id": "123",
            "type": "dataServices",
            "uri": "https://data-services.digdir.no/321",
            "identifier": "321",
            "title": {
                "nb": "data service nb",
                "nn": "data service nn",
                "en": "data service en"
            },
            "description": {
                "nb": "data service desc nb",
                "nn": "data service desc nn",
                "en": "data service desc en"
            }
        }
    }
);
db.dataServices.insert(
    {
        "_id": "111",
        "removed": false,
        "resource": {
            "id": "111",
            "type": "dataServices",
            "uri": "https://data-services.digdir.no/654",
            "identifier": "654",
            "title": {
                "nb": "data service nb",
                "nn": "data service nn",
                "en": "data service en"
            },
            "description": {
                "nb": "data service desc nb",
                "nn": "data service desc nn",
                "en": "data service desc en"
            }
        }
    }
);
db.dataServices.insert(
    {
        "_id": "222",
        "removed": true,
        "resource": {
            "id": "222",
            "type": "dataServices",
            "uri": "https://data-services.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "removed data service nb",
                "nn": "removed data service nn",
                "en": "removed data service en"
            },
            "description": {
                "nb": "removed data service desc nb",
                "nn": "removed data service desc nn",
                "en": "removed data service desc en"
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

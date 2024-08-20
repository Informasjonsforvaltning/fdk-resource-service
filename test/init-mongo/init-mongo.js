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
        "timestamp": 10,
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
        "deleted": false,
        "timestamp": 10,
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
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "222",
            "type": "datasets",
            "uri": "https://datasets.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "deleted dataset nb",
                "nn": "deleted dataset nn",
                "en": "deleted dataset en"
            },
            "description": {
                "nb": "deleted dataset desc nb",
                "nn": "deleted dataset desc nn",
                "en": "deleted dataset desc en"
            }
        }
    }
);
db.datasets.insert(
    {
        "_id": "333",
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "333"
        }
    }
);
db.datasets.insert(
    {
        "_id": "444",
        "deleted": false,
        "timestamp": 10,
        "resource": {
            "id": "444"
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
        "timestamp": 10,
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
        "deleted": false,
        "timestamp": 10,
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
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "222",
            "type": "services",
            "uri": "https://services.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "deleted service nb",
                "nn": "deleted service nn",
                "en": "deleted service en"
            },
            "description": {
                "nb": "deleted service desc nb",
                "nn": "deleted service desc nn",
                "en": "deleted service desc en"
            }
        }
    }
);
db.services.insert(
    {
        "_id": "333",
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "333"
        }
    }
);
db.services.insert(
    {
        "_id": "444",
        "deleted": false,
        "timestamp": 10,
        "resource": {
            "id": "444"
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
        "timestamp": 10,
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
        "deleted": false,
        "timestamp": 10,
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
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "222",
            "type": "events",
            "uri": "https://events.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "deleted event nb",
                "nn": "deleted event nn",
                "en": "deleted event en"
            },
            "description": {
                "nb": "deleted event desc nb",
                "nn": "deleted event desc nn",
                "en": "deleted event desc en"
            }
        }
    }
);
db.events.insert(
    {
        "_id": "333",
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "333"
        }
    }
);
db.events.insert(
    {
        "_id": "444",
        "deleted": false,
        "timestamp": 10,
        "resource": {
            "id": "444"
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
        "timestamp": 10,
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
        "deleted": false,
        "timestamp": 10,
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
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "222",
            "type": "informationModels",
            "uri": "https://information-models.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "deleted information model nb",
                "nn": "deleted information model nn",
                "en": "deleted information model en"
            },
            "description": {
                "nb": "deleted information model desc nb",
                "nn": "deleted information model desc nn",
                "en": "deleted information model desc en"
            }
        }
    }
);
db.informationModels.insert(
    {
        "_id": "333",
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "333"
        }
    }
);
db.informationModels.insert(
    {
        "_id": "444",
        "deleted": false,
        "timestamp": 10,
        "resource": {
            "id": "444"
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
        "timestamp": 10,
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
        "deleted": false,
        "timestamp": 10,
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
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "222",
            "type": "dataServices",
            "uri": "https://data-services.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "deleted data service nb",
                "nn": "deleted data service nn",
                "en": "deleted data service en"
            },
            "description": {
                "nb": "deleted data service desc nb",
                "nn": "deleted data service desc nn",
                "en": "deleted data service desc en"
            }
        }
    }
);
db.dataServices.insert(
    {
        "_id": "333",
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "333"
        }
    }
);
db.dataServices.insert(
    {
        "_id": "444",
        "deleted": false,
        "timestamp": 10,
        "resource": {
            "id": "444"
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
        "timestamp": 10,
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
        "deleted": false,
        "timestamp": 10,
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
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "222",
            "type": "concepts",
            "uri": "https://concepts.digdir.no/777",
            "identifier": "777",
            "title": {
                "nb": "deleted concept nb",
                "nn": "deleted concept nn",
                "en": "deleted concept en"
            },
            "description": {
                "nb": "deleted concept desc nb",
                "nn": "deleted concept desc nn",
                "en": "deleted concept desc en"
            }
        }
    }
);
db.concepts.insert(
    {
        "_id": "333",
        "deleted": true,
        "timestamp": 10,
        "resource": {
            "id": "333"
        }
    }
);
db.concepts.insert(
    {
        "_id": "444",
        "deleted": false,
        "timestamp": 10,
        "resource": {
            "id": "444"
        }
    }
);

db = db.getSiblingDB('fdkResourceService');
db.createCollection('datasets');
db.datasets.insert(
    {
        "_id": "123",
        "dataset": {
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
        "dataset": {
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
package model

type Dataset struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

type DatasetDBO struct {
	ID      string  `bson:"_id"`
	Dataset Dataset `bson:"dataset"`
	Removed bool    `bson:"removed"`
}

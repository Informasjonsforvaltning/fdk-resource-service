package model

type DBO struct {
	ID       string                 `bson:"_id"`
	Resource map[string]interface{} `bson:"resource"`
	Removed  bool                   `bson:"removed"`
}

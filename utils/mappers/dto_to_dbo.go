package mappers

import "github.com/Informasjonsforvaltning/fdk-resource-service/model"

func ToDBO(dto map[string]interface{}) model.DBO {
	return model.DBO{
		ID:       dto["id"].(string),
		Resource: dto,
		Removed:  false,
	}
}

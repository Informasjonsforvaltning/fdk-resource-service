package mappers

import "github.com/Informasjonsforvaltning/fdk-resource-service/model"

func ToDBO(dtoList []map[string]interface{}) []model.DBO {
	var dboList []model.DBO
	for _, dto := range dtoList {
		var dbo = model.DBO{
			ID:       dto["id"].(string),
			Resource: dto,
			Removed:  false,
		}
		dboList = append(dboList, dbo)
	}
	return dboList
}

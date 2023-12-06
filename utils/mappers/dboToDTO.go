package mappers

import "github.com/Informasjonsforvaltning/fdk-resource-service/model"

func ToDTO(dboList []model.DBO) []map[string]interface{} {
	var dtoList []map[string]interface{}
	for _, dbo := range dboList {
		dtoList = append(dtoList, dbo.Resource)
	}
	return dtoList
}

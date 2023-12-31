// Code generated by github.com/actgardner/gogen-avro/v10. DO NOT EDIT.
/*
 * SOURCE:
 *     data_service_schema.avsc
 */
package avro

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/actgardner/gogen-avro/v10/vm"
	"github.com/actgardner/gogen-avro/v10/vm/types"
)

var _ = fmt.Printf

type DataServiceEventType int32

const (
	DataServiceEventTypeDATA_SERVICE_HARVESTED DataServiceEventType = 0
	DataServiceEventTypeDATA_SERVICE_REASONED  DataServiceEventType = 1
	DataServiceEventTypeDATA_SERVICE_REMOVED   DataServiceEventType = 2
)

func (e DataServiceEventType) String() string {
	switch e {
	case DataServiceEventTypeDATA_SERVICE_HARVESTED:
		return "DATA_SERVICE_HARVESTED"
	case DataServiceEventTypeDATA_SERVICE_REASONED:
		return "DATA_SERVICE_REASONED"
	case DataServiceEventTypeDATA_SERVICE_REMOVED:
		return "DATA_SERVICE_REMOVED"
	}
	return "unknown"
}

func writeDataServiceEventType(r DataServiceEventType, w io.Writer) error {
	return vm.WriteInt(int32(r), w)
}

func NewDataServiceEventTypeValue(raw string) (r DataServiceEventType, err error) {
	switch raw {
	case "DATA_SERVICE_HARVESTED":
		return DataServiceEventTypeDATA_SERVICE_HARVESTED, nil
	case "DATA_SERVICE_REASONED":
		return DataServiceEventTypeDATA_SERVICE_REASONED, nil
	case "DATA_SERVICE_REMOVED":
		return DataServiceEventTypeDATA_SERVICE_REMOVED, nil
	}

	return -1, fmt.Errorf("invalid value for DataServiceEventType: '%s'", raw)

}

func (b DataServiceEventType) MarshalJSON() ([]byte, error) {
	return json.Marshal(b.String())
}

func (b *DataServiceEventType) UnmarshalJSON(data []byte) error {
	var stringVal string
	err := json.Unmarshal(data, &stringVal)
	if err != nil {
		return err
	}
	val, err := NewDataServiceEventTypeValue(stringVal)
	*b = val
	return err
}

type DataServiceEventTypeWrapper struct {
	Target *DataServiceEventType
}

func (b DataServiceEventTypeWrapper) SetBoolean(v bool) {
	panic("Unable to assign boolean to int field")
}

func (b DataServiceEventTypeWrapper) SetInt(v int32) {
	*(b.Target) = DataServiceEventType(v)
}

func (b DataServiceEventTypeWrapper) SetLong(v int64) {
	panic("Unable to assign long to int field")
}

func (b DataServiceEventTypeWrapper) SetFloat(v float32) {
	panic("Unable to assign float to int field")
}

func (b DataServiceEventTypeWrapper) SetUnionElem(v int64) {
	panic("Unable to assign union elem to int field")
}

func (b DataServiceEventTypeWrapper) SetDouble(v float64) {
	panic("Unable to assign double to int field")
}

func (b DataServiceEventTypeWrapper) SetBytes(v []byte) {
	panic("Unable to assign bytes to int field")
}

func (b DataServiceEventTypeWrapper) SetString(v string) {
	panic("Unable to assign string to int field")
}

func (b DataServiceEventTypeWrapper) Get(i int) types.Field {
	panic("Unable to get field from int field")
}

func (b DataServiceEventTypeWrapper) SetDefault(i int) {
	panic("Unable to set default on int field")
}

func (b DataServiceEventTypeWrapper) AppendMap(key string) types.Field {
	panic("Unable to append map key to from int field")
}

func (b DataServiceEventTypeWrapper) AppendArray() types.Field {
	panic("Unable to append array element to from int field")
}

func (b DataServiceEventTypeWrapper) NullField(int) {
	panic("Unable to null field in int field")
}

func (b DataServiceEventTypeWrapper) HintSize(int) {
	panic("Unable to hint size in int field")
}

func (b DataServiceEventTypeWrapper) Finalize() {}

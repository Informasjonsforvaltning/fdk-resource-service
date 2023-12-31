// Code generated by github.com/actgardner/gogen-avro/v10. DO NOT EDIT.
/*
 * SOURCE:
 *     concept_schema.avsc
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

type ConceptEventType int32

const (
	ConceptEventTypeCONCEPT_HARVESTED ConceptEventType = 0
	ConceptEventTypeCONCEPT_REASONED  ConceptEventType = 1
	ConceptEventTypeCONCEPT_REMOVED   ConceptEventType = 2
)

func (e ConceptEventType) String() string {
	switch e {
	case ConceptEventTypeCONCEPT_HARVESTED:
		return "CONCEPT_HARVESTED"
	case ConceptEventTypeCONCEPT_REASONED:
		return "CONCEPT_REASONED"
	case ConceptEventTypeCONCEPT_REMOVED:
		return "CONCEPT_REMOVED"
	}
	return "unknown"
}

func writeConceptEventType(r ConceptEventType, w io.Writer) error {
	return vm.WriteInt(int32(r), w)
}

func NewConceptEventTypeValue(raw string) (r ConceptEventType, err error) {
	switch raw {
	case "CONCEPT_HARVESTED":
		return ConceptEventTypeCONCEPT_HARVESTED, nil
	case "CONCEPT_REASONED":
		return ConceptEventTypeCONCEPT_REASONED, nil
	case "CONCEPT_REMOVED":
		return ConceptEventTypeCONCEPT_REMOVED, nil
	}

	return -1, fmt.Errorf("invalid value for ConceptEventType: '%s'", raw)

}

func (b ConceptEventType) MarshalJSON() ([]byte, error) {
	return json.Marshal(b.String())
}

func (b *ConceptEventType) UnmarshalJSON(data []byte) error {
	var stringVal string
	err := json.Unmarshal(data, &stringVal)
	if err != nil {
		return err
	}
	val, err := NewConceptEventTypeValue(stringVal)
	*b = val
	return err
}

type ConceptEventTypeWrapper struct {
	Target *ConceptEventType
}

func (b ConceptEventTypeWrapper) SetBoolean(v bool) {
	panic("Unable to assign boolean to int field")
}

func (b ConceptEventTypeWrapper) SetInt(v int32) {
	*(b.Target) = ConceptEventType(v)
}

func (b ConceptEventTypeWrapper) SetLong(v int64) {
	panic("Unable to assign long to int field")
}

func (b ConceptEventTypeWrapper) SetFloat(v float32) {
	panic("Unable to assign float to int field")
}

func (b ConceptEventTypeWrapper) SetUnionElem(v int64) {
	panic("Unable to assign union elem to int field")
}

func (b ConceptEventTypeWrapper) SetDouble(v float64) {
	panic("Unable to assign double to int field")
}

func (b ConceptEventTypeWrapper) SetBytes(v []byte) {
	panic("Unable to assign bytes to int field")
}

func (b ConceptEventTypeWrapper) SetString(v string) {
	panic("Unable to assign string to int field")
}

func (b ConceptEventTypeWrapper) Get(i int) types.Field {
	panic("Unable to get field from int field")
}

func (b ConceptEventTypeWrapper) SetDefault(i int) {
	panic("Unable to set default on int field")
}

func (b ConceptEventTypeWrapper) AppendMap(key string) types.Field {
	panic("Unable to append map key to from int field")
}

func (b ConceptEventTypeWrapper) AppendArray() types.Field {
	panic("Unable to append array element to from int field")
}

func (b ConceptEventTypeWrapper) NullField(int) {
	panic("Unable to null field in int field")
}

func (b ConceptEventTypeWrapper) HintSize(int) {
	panic("Unable to hint size in int field")
}

func (b ConceptEventTypeWrapper) Finalize() {}

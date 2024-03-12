// Code generated by github.com/actgardner/gogen-avro/v10. DO NOT EDIT.
/*
 * SOURCE:
 *     parsed.avsc
 */
package avro

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/actgardner/gogen-avro/v10/compiler"
	"github.com/actgardner/gogen-avro/v10/vm"
	"github.com/actgardner/gogen-avro/v10/vm/types"
)

var _ = fmt.Printf

type RdfParseEvent struct {
	ResourceType RdfParseResourceType `json:"resourceType"`

	FdkId string `json:"fdkId"`

	Data string `json:"data"`

	Timestamp int64 `json:"timestamp"`
}

const RdfParseEventAvroCRC64Fingerprint = "\xd7\b?\xe9\xf5\xc1C\xc9"

func NewRdfParseEvent() RdfParseEvent {
	r := RdfParseEvent{}
	return r
}

func DeserializeRdfParseEvent(r io.Reader) (RdfParseEvent, error) {
	t := NewRdfParseEvent()
	deser, err := compiler.CompileSchemaBytes([]byte(t.Schema()), []byte(t.Schema()))
	if err != nil {
		return t, err
	}

	err = vm.Eval(r, deser, &t)
	return t, err
}

func DeserializeRdfParseEventFromSchema(r io.Reader, schema string) (RdfParseEvent, error) {
	t := NewRdfParseEvent()

	deser, err := compiler.CompileSchemaBytes([]byte(schema), []byte(t.Schema()))
	if err != nil {
		return t, err
	}

	err = vm.Eval(r, deser, &t)
	return t, err
}

func writeRdfParseEvent(r RdfParseEvent, w io.Writer) error {
	var err error
	err = writeRdfParseResourceType(r.ResourceType, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.FdkId, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.Data, w)
	if err != nil {
		return err
	}
	err = vm.WriteLong(r.Timestamp, w)
	if err != nil {
		return err
	}
	return err
}

func (r RdfParseEvent) Serialize(w io.Writer) error {
	return writeRdfParseEvent(r, w)
}

func (r RdfParseEvent) Schema() string {
	return "{\"fields\":[{\"name\":\"resourceType\",\"type\":{\"name\":\"RdfParseResourceType\",\"symbols\":[\"DATASET\",\"DATASERVICE\",\"CONCEPT\",\"INFORMATIONMODEL\",\"SERVICE\",\"EVENT\"],\"type\":\"enum\"}},{\"name\":\"fdkId\",\"type\":\"string\"},{\"name\":\"data\",\"type\":\"string\"},{\"logicalType\":\"timestamp-millis\",\"name\":\"timestamp\",\"type\":\"long\"}],\"name\":\"no.fdk.rdf.parse.RdfParseEvent\",\"type\":\"record\"}"
}

func (r RdfParseEvent) SchemaName() string {
	return "no.fdk.rdf.parse.RdfParseEvent"
}

func (_ RdfParseEvent) SetBoolean(v bool)    { panic("Unsupported operation") }
func (_ RdfParseEvent) SetInt(v int32)       { panic("Unsupported operation") }
func (_ RdfParseEvent) SetLong(v int64)      { panic("Unsupported operation") }
func (_ RdfParseEvent) SetFloat(v float32)   { panic("Unsupported operation") }
func (_ RdfParseEvent) SetDouble(v float64)  { panic("Unsupported operation") }
func (_ RdfParseEvent) SetBytes(v []byte)    { panic("Unsupported operation") }
func (_ RdfParseEvent) SetString(v string)   { panic("Unsupported operation") }
func (_ RdfParseEvent) SetUnionElem(v int64) { panic("Unsupported operation") }

func (r *RdfParseEvent) Get(i int) types.Field {
	switch i {
	case 0:
		w := RdfParseResourceTypeWrapper{Target: &r.ResourceType}

		return w

	case 1:
		w := types.String{Target: &r.FdkId}

		return w

	case 2:
		w := types.String{Target: &r.Data}

		return w

	case 3:
		w := types.Long{Target: &r.Timestamp}

		return w

	}
	panic("Unknown field index")
}

func (r *RdfParseEvent) SetDefault(i int) {
	switch i {
	}
	panic("Unknown field index")
}

func (r *RdfParseEvent) NullField(i int) {
	switch i {
	}
	panic("Not a nullable field index")
}

func (_ RdfParseEvent) AppendMap(key string) types.Field { panic("Unsupported operation") }
func (_ RdfParseEvent) AppendArray() types.Field         { panic("Unsupported operation") }
func (_ RdfParseEvent) HintSize(int)                     { panic("Unsupported operation") }
func (_ RdfParseEvent) Finalize()                        {}

func (_ RdfParseEvent) AvroCRC64Fingerprint() []byte {
	return []byte(RdfParseEventAvroCRC64Fingerprint)
}

func (r RdfParseEvent) MarshalJSON() ([]byte, error) {
	var err error
	output := make(map[string]json.RawMessage)
	output["resourceType"], err = json.Marshal(r.ResourceType)
	if err != nil {
		return nil, err
	}
	output["fdkId"], err = json.Marshal(r.FdkId)
	if err != nil {
		return nil, err
	}
	output["data"], err = json.Marshal(r.Data)
	if err != nil {
		return nil, err
	}
	output["timestamp"], err = json.Marshal(r.Timestamp)
	if err != nil {
		return nil, err
	}
	return json.Marshal(output)
}

func (r *RdfParseEvent) UnmarshalJSON(data []byte) error {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}

	var val json.RawMessage
	val = func() json.RawMessage {
		if v, ok := fields["resourceType"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.ResourceType); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for resourceType")
	}
	val = func() json.RawMessage {
		if v, ok := fields["fdkId"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.FdkId); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for fdkId")
	}
	val = func() json.RawMessage {
		if v, ok := fields["data"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Data); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for data")
	}
	val = func() json.RawMessage {
		if v, ok := fields["timestamp"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Timestamp); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for timestamp")
	}
	return nil
}
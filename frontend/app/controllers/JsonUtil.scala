package controllers

import com.fasterxml.jackson.core.{Version, JsonGenerator}
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer, JsonNode, ObjectMapper}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.linkedin.data.DataMap
import com.linkedin.data.codec.JacksonDataCodec
import com.linkedin.data.template.{RecordTemplate, JacksonDataTemplateCodec, DataTemplate}
import play.api.libs.json.{Json, JsValue}
import play.libs.F
import scala.collection.JavaConversions
import play.api.Play
import com.fasterxml.jackson.databind.module.SimpleModule
import java.util.concurrent.atomic.{AtomicLong, AtomicInteger}

/**
 * Utility methods for dealing with JSON
 */
object JsonUtil {

  /**
   * Jackson codec for serializing/deserializing the rest.li DataMap and DataList classes
   */
  val jacksonDataCodec = new JacksonDataCodec()

  /**
   * Jackson codec for serializing/deserializing rest.li RecordTemplate classes
   */
  val jacksonDataTemplateCodec = new JacksonDataTemplateCodec()

  /**
   * The Jackson ObjectMapper instance that is initialized with serializers for a few special cases:
   *
   * 1. rest.li RecordTemplates and DataMaps
   * 2. Special handling for numbers to avoid JavaScript overflow errors
   * 3. Play F.Option class
   *
   * More info on rest.li RecordTemplates: these classes have conflicting methods for a single field (e.g. getFoo and isFoo)
   * that Jackson cannot handle. The getters also throw an exception if a "required" field is null, but we don't
   * necessarily want this validation when converting the object to JSON. Therefore, we add a couple Jackson serializers
   * to handle RecordTemplate and DataMap objects.
   *
   */
  val mapper = new ObjectMapper()

  private val serializerModule = new SimpleModule("JsonUtilSerializerModule", new Version(1, 0, 0, null, null, null))
  serializerModule.addSerializer(classOf[RecordTemplate], new RecordTemplateSerializer)
  serializerModule.addSerializer(classOf[DataMap], new DataMapSerializer)
  serializerModule.addSerializer(classOf[Number], new JavascriptNumberSerializer)
  serializerModule.addSerializer(classOf[F.Option[Any]], new JavaOptionSerializer)
  serializerModule.addSerializer(classOf[JsValue], new JsValueSerializer)

  mapper.registerModule(DefaultScalaModule)
  mapper.registerModule(serializerModule)

  /**
   * Convert a Scala Map where the value type is Seq to a Java Map where the value type is Array
   *
   * @param map
   * @return
   */
  def toJavaMap(map: Map[String, Seq[String]]): java.util.Map[String, Array[String]] = {
    JavaConversions.mapAsJavaMap(map.map(e => e._1 -> e._2.toArray))
  }

  /**
   * Convert a Java Map where the value type is Array to a Scala Map where the value type is Seq
   *
   * @param map
   * @return
   */
  def toScalaMap(map: java.util.Map[String, Array[String]]): Map[String, Seq[String]] = {
    JavaConversions.mapAsScalaMap(map).toMap.map(entry => entry._1 -> entry._2.toSeq)
  }

  /**
   * Converts a POJO to a Map where keys are the POJO's fields and values are the values for those fields. This is done
   * by converting the POJO to a JSON string using toJsonString and then parsing the JSON string into a Map using
   * Jackson.
   *
   * @deprecated use toMap
   * @param pojo
   * @return
   */
  def pojoAsMap(pojo: Any): Map[String, Any] = {
    toMap(pojo)
  }

  /**
   * Scala API. Converts an object to a Map where keys are the object's fields and values are the values for those
   * fields. This is done by converting the POJO to a JSON string using toJsonString and then parsing the JSON string
   * into a Map using Jackson.
   *
   * @param obj
   * @return
   */
  def toMap(obj: Any): Map[String, Any] = {
    Option(obj).map {
      case m: Map[_, _] =>
        m.asInstanceOf[Map[String, Any]]
      case s: String =>
        mapper.readValue(s, classOf[Map[String, Any]])
      case obj: Any =>
        mapper.readValue(toJsonString(obj), classOf[Map[String, Any]])
    }.getOrElse(Map[String, Any]())
  }

  /**
   * Convert a POJO to a String. Uses Jackson underneath, but also handles rest.li RecordTemplates correctly. If pojo is
   * null, returns an empty JSON object ({}).
   *
   * @deprecated use toJsonString
   * @param pojo
   * @return
   */
  def pojoAsJsonString(pojo: Any): String = {
    toJsonString(pojo)
  }

  /**
   * Convert an object to a JSON String. Uses Jackson underneath, but also handles rest.li RecordTemplates correctly.
   * If the object is null, returns an empty JSON object ({}).
   *
   * @param obj
   * @return
   */
  def toJsonString(obj: Any): String = {
    if (obj == null) {
      "{}"
    } else {
      mapper.writeValueAsString(obj)
    }
  }

  /**
   * Convert a DataMap to a JSON String
   *
   * @param map
   * @return
   */
  def dataMapToJsonString(map: DataMap): String = {
    new String(jacksonDataCodec.mapToBytes(map))
  }

  /**
   * Convert a rest.li RecordTemplate to a JSON String
   *
   * @param template
   * @return
   */
  def dataTemplateToJsonString(template: DataTemplate[_]): String = {
    new String(jacksonDataTemplateCodec.dataTemplateToBytes(template))
  }

  /**
   * Java API. Convert a Java object to a Jackson JsonNode. This is done by first converting the object to a JSON
   * String and then parsing it using Jackson.
   *
   * @param obj
   * @return
   */
  def toJsonNode(obj: Any): JsonNode = {
    mapper.readValue(toJsonString(obj), classOf[JsonNode])
  }

  /**
   * Scala API. Convert a Scala object to a Play JsValue. This is done by first converting the object to a JSON
   * String and then parsing it using Play's Json library.
   *
   * @param obj
   * @return
   */
  def toJsValue(obj: Any): JsValue = {
    play.api.libs.json.Json.parse(toJsonString(obj))
  }

  /**
   * A (possibly inefficient) way to convert any object into a DataMap by:
   *
   * 1. Converting it into JSON using Jackson
   * 2. Using the JacsonDataCode to parse the JSON into a DataMap
   *
   * @param obj
   * @return
   */
  def toDataMap(obj: Any): DataMap = jacksonDataCodec.bytesToMap(toJsonString(obj).getBytes("UTF-8"))
}

class RecordTemplateSerializer extends JsonSerializer[RecordTemplate] {

  def serialize(template: RecordTemplate, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeRawValue(JsonUtil.dataTemplateToJsonString(template))
  }
}

class DataMapSerializer extends JsonSerializer[DataMap] {

  def serialize(map: DataMap, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeRawValue(JsonUtil.dataMapToJsonString(map))
  }
}

class JavaOptionSerializer extends JsonSerializer[F.Option[Any]] {

  def serialize(option: F.Option[Any], generator: JsonGenerator, provider: SerializerProvider) {
    if (option.isDefined) {
      generator.writeObject(option.get())
    } else {
      generator.writeNull()
    }
  }
}

class JsValueSerializer extends JsonSerializer[JsValue] {

  def serialize(value: JsValue, generator: JsonGenerator, provider: SerializerProvider) {
    generator.writeRawValue(Json.stringify(value))
  }
}

/**
 * Handles the limits of Javascript's number class. Javascript can only handle values up to 2**53. In order to handle
 * this restriction of Javascript, all numbers over this value are being cast to strings for serialization
 * StackOverflow: http://stackoverflow.com/questions/307179/what-is-javascripts-max-int-whats-the-highest-integer-value-a-number-can-go-t
 */
class JavascriptNumberSerializer extends JsonSerializer[Number] {
  private lazy val JavascriptMaxNumber = new java.lang.Long("9007199254740992")

  def serialize(number: Number, generator: JsonGenerator, provider: SerializerProvider) {
    val convertOverflow = Play.maybeApplication.flatMap(_.configuration.getBoolean("json.serializers.convertOverflowToString")).getOrElse(true)
    lazy val value = number.longValue()
    lazy val doesOverflow = value > JavascriptMaxNumber || value < -JavascriptMaxNumber

    if (convertOverflow && doesOverflow) {
      generator.writeString(number.toString)
    } else {
      number match {
        case _: java.lang.Long | _:java.lang.Integer | _:java.lang.Short | _:java.lang.Byte | _:AtomicLong | _:AtomicInteger =>
          generator.writeNumber(value)
        case _: java.lang.Double =>
          generator.writeNumber(number.doubleValue)
        case _: java.lang.Float =>
          generator.writeNumber(number.floatValue)
        case bigDecimal:java.math.BigDecimal =>
          generator.writeNumber(bigDecimal)
        case bigInteger: java.math.BigInteger =>
          generator.writeNumber(bigInteger)
        case _ =>
          generator.writeNumber(number.toString)
      }
    }
  }
}
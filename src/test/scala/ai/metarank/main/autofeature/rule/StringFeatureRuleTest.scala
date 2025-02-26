package ai.metarank.main.autofeature.rule

import ai.metarank.feature.StringFeature.EncoderName.{IndexEncoderName, OnehotEncoderName}
import ai.metarank.feature.StringFeature.StringFeatureSchema
import ai.metarank.main.command.autofeature.FieldStat.StringFieldStat
import ai.metarank.main.command.autofeature.{EventModel, ItemFieldStat}
import ai.metarank.main.command.autofeature.rules.StringFeatureRule
import ai.metarank.model.FieldName
import ai.metarank.model.FieldName.EventType.Item
import ai.metarank.model.Key.FeatureName
import ai.metarank.model.ScopeType.ItemScopeType
import cats.data.NonEmptyList
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringFeatureRuleTest extends AnyFlatSpec with Matchers {
  it should "skip constant fields" in {
    val result = StringFeatureRule().make("color", StringFieldStat(Map("red" -> 100)))
    result shouldBe None
  }

  it should "prefer onehot encoding for binary fields" in {
    val result = StringFeatureRule().make("available", StringFieldStat(Map("yes" -> 100, "no" -> 100)))
    result shouldBe Some(
      StringFeatureSchema(
        name = FeatureName("available"),
        source = FieldName(Item, "available"),
        scope = ItemScopeType,
        encode = Some(OnehotEncoderName),
        values = NonEmptyList.of("yes", "no")
      )
    )
  }

  it should "not drop infreq values for low-cardinality fields" in {
    val result = StringFeatureRule().make("color", StringFieldStat(Map("red" -> 10, "green" -> 1, "blue" -> 1)))
    result shouldBe Some(
      StringFeatureSchema(
        name = FeatureName("color"),
        source = FieldName(Item, "color"),
        scope = ItemScopeType,
        encode = Some(OnehotEncoderName),
        values = NonEmptyList.of("red", "green", "blue")
      )
    )
  }

  it should "drop infreq values for high-cardinality fields" in {
    val result =
      StringFeatureRule().make("color", StringFieldStat((0 until 20).map(i => s"c$i" -> i).toMap))
    result shouldBe Some(
      StringFeatureSchema(
        name = FeatureName("color"),
        source = FieldName(Item, "color"),
        scope = ItemScopeType,
        encode = Some(IndexEncoderName),
        values = NonEmptyList.of("c19", "c18", "c17", "c16", "c15", "c14", "c13", "c12", "c11", "c10", "c9", "c8", "c7")
      )
    )
  }
}

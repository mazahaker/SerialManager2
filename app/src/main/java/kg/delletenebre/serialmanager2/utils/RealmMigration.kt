package kg.delletenebre.serialmanager2.utils

import io.realm.DynamicRealm
import io.realm.FieldAttribute

open class RealmMigration : io.realm.RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var currentVersion = oldVersion
        val schema = realm.schema

        // Migrate from version 0 to version 1
        if (currentVersion == 0L) {
            val commandSchema = schema.get("Command")
            commandSchema!!
                    .addField("notyDuration_temp", Float::class.javaPrimitiveType)
                    .transform { obj -> obj.setFloat("notyDuration_temp", obj.getInt("notyDuration").toFloat()) }
                    .removeField("notyDuration")
                    .renameField("notyDuration_temp", "notyDuration")
            currentVersion++
        }

        if (currentVersion == 1L) {
            schema.create("WidgetReceiveModel")
                    .addField("index", Int::class.java, FieldAttribute.REQUIRED)
                    .addField("key", String::class.java, FieldAttribute.REQUIRED)
                    .addField("value", String::class.java)
                    .addField("textColor", String::class.java)
                    .addField("textSize", Int::class.javaPrimitiveType)
                    .addField("backgroundColor", String::class.java)
                    .addField("backgroundImage", String::class.java)
                    .addField("layoutAlignId", Int::class.javaPrimitiveType)
                    .addField("textAlignId", Int::class.javaPrimitiveType)

            currentVersion++
        }

        if (currentVersion == 2L) {
            val commandSchema = schema.get("Command")
            commandSchema!!
                    .addField("positionZ", Int::class.javaPrimitiveType)
                    .transform { obj -> obj.setInt("positionZ", 0) }
            currentVersion++
        }

        if (currentVersion == 3L) {
            schema.remove("WidgetReceiveModel")
            schema.create("WidgetSimpleModel")
                    .addField("id", Int::class.java, FieldAttribute.PRIMARY_KEY)
                    .addField("key", String::class.java)
                    .addField("value", String::class.java)
                    .addField("textColor", String::class.java)
                    .addField("textSize", Int::class.java)
                    .addField("backgroundColor", String::class.java)
                    .addField("backgroundImage", String::class.java)
                    .addField("layoutAlignId", Int::class.java)
                    .addField("textAlignId", Int::class.java)

            currentVersion++
        }
    }
}
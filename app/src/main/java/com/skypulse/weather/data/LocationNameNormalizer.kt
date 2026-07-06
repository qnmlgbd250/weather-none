package com.skypulse.weather.data

object LocationNameNormalizer {
    private val genericSubArea = Regex(
        "^(?:[一二三四五六七八九十]+|\\d+|[A-Za-z]|东|南|西|北|中)区$"
    )
    private val pureNumberAddress = Regex("^\\d+[号弄栋幢单元室]?")

    fun normalizeAdminPart(value: String?): String? {
        val cleaned = normalizeBasic(value) ?: return null
        return cleaned.takeUnless { isInvalidDisplayName(it) }
    }

    fun normalizePoiPart(value: String?): String? {
        val cleaned = normalizeBasic(value) ?: return null
        return cleaned
            .removePrefix("中国")
            .takeUnless { isInvalidDisplayName(it) }
    }

    fun normalizeAddressDetail(value: String?): String? {
        val normalized = normalizeBasic(value)?.removePrefix("中国") ?: return null
        val withoutAdmin = normalized.removeAdministrativePrefixForAddress()
        val cleaned = withoutAdmin.takeIf { it.isNotBlank() } ?: return null
        return cleaned.takeUnless { isInvalidDisplayName(it) }
    }

    fun isInvalidDisplayName(value: String?): Boolean {
        val name = value?.trim()?.takeIf { it.isNotBlank() } ?: return true
        return name == "null" ||
            name == "中国" ||
            name == "中华人民共和国" ||
            genericSubArea.matches(name) ||
            pureNumberAddress.matches(name)
    }

    private fun normalizeBasic(value: String?): String? {
        return value
            ?.replace(Regex("\\s+"), "")
            ?.replace("附近", "")
            ?.replace("中国", "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun String.removeAdministrativePrefixForAddress(): String {
        var result = this
        result = result.replace(Regex("^.*?(?:省|自治区|特别行政区)"), "")
        result = result.replace(Regex("^.*?(?:市|自治州|地区|盟)"), "")

        val districtPrefix = Regex("^(.+?(?:区|县|自治县|旗))").find(result)?.groupValues?.getOrNull(1)
        if (districtPrefix != null && !districtPrefix.hasNonAdministrativeZoneSuffix()) {
            result = result.removePrefix(districtPrefix)
        }
        return result
    }

    private fun String.hasNonAdministrativeZoneSuffix(): Boolean {
        return listOf(
            "园区", "小区", "校区", "厂区", "片区", "港区", "库区", "景区",
            "矿区", "病区", "馆区", "院区", "展区", "生活区", "工业区", "开发区", "保税区"
        ).any { endsWith(it) }
    }
}

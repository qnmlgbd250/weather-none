package com.skypulse.weather.data

object LocationNameNormalizer {
    private val landmarkSuffixPattern =
        "大厦|大楼|写字楼|中心|广场|大厅|公馆|公寓|小区|花园|阁|轩|馆|院|大戏院|剧院|学校|大学|医院|大酒店|酒店|商厦|大商场|商场|超市|大门|正门|北门|南门|东门|西门|地铁站|公交站|厂|大厂|TCL"

    private val buildingAfterAddressAnchor = Regex(
        "(?:街|路|道|巷|号|弄|区|园|村)([^街路道巷号弄区园村]{2,}?(?:$landmarkSuffixPattern))$"
    )
    private val wholeLandmark = Regex("^.{2,18}(?:$landmarkSuffixPattern)$")
    private val usefulPlacePattern = Regex(
        "(.+?(?:街道|大道|大街|公路|高速|快速路|路|街|巷|弄|镇|乡|村|社区|广场|公园|园区|商圈))(?:\\d+.*|[甲乙丙丁戊己庚辛壬癸]座.*|[东西南北中]门.*|出入口.*|附近.*|$)"
    )
    private val genericSubArea = Regex(
        "^(?:[一二三四五六七八九十]+|\\d+|[A-Za-z]|东|南|西|北|中)区$"
    )
    private val pureNumberAddress = Regex("^\\d+[号弄栋幢单元室]?")

    fun normalizeAdminPart(value: String?): String? {
        val cleaned = normalizeBasic(value) ?: return null
        return cleaned.takeUnless { isInvalidDisplayName(it) }?.take(14)
    }

    fun normalizePoiPart(value: String?): String? {
        val cleaned = normalizeBasic(value) ?: return null
        return cleaned
            .removePrefix("中国")
            .takeUnless { isInvalidDisplayName(it) }
            ?.take(14)
    }

    fun normalizeAddressDetail(value: String?): String? {
        val normalized = normalizeBasic(value)?.removePrefix("中国") ?: return null
        val withoutAdmin = normalized.removeAdministrativePrefixForAddress()
        val building = withoutAdmin.extractBuildingNameFromAddress()
        val cleaned = (building ?: withoutAdmin.truncateAfterUsefulLocationSuffix())
            .takeIf { it.isNotBlank() }
            ?: return null
        return cleaned.takeUnless { isInvalidDisplayName(it) }?.take(14)
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

    private fun String.extractBuildingNameFromAddress(): String? {
        buildingAfterAddressAnchor.find(this)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            if (candidate.length >= 2) return candidate
        }
        return if (wholeLandmark.matches(this)) this else null
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

    private fun String.truncateAfterUsefulLocationSuffix(): String {
        val match = usefulPlacePattern.find(this)
        if (match != null) return match.groupValues[1]
        return replace(Regex("\\d+号.*$"), "")
            .replace(Regex("\\d+弄.*$"), "")
            .replace(Regex("\\d+栋.*$"), "")
            .replace(Regex("\\d+幢.*$"), "")
    }
}

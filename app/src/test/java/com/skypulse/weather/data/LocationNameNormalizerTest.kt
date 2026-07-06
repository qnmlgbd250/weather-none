package com.skypulse.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationNameNormalizerTest {
    @Test
    fun `admin part rejects generic zone fragments`() {
        assertNull(LocationNameNormalizer.normalizeAdminPart("四区"))
        assertNull(LocationNameNormalizer.normalizeAdminPart("A区"))
        assertNull(LocationNameNormalizer.normalizeAdminPart("东区"))
    }

    @Test
    fun `address detail does not collapse administrative text to generic zone`() {
        assertNull(LocationNameNormalizer.normalizeAddressDetail("北京市朝阳区四区"))
        assertEquals("中关村南四区", LocationNameNormalizer.normalizeAddressDetail("北京市海淀区中关村南四区附近"))
    }

    @Test
    fun `address detail preserves non administrative zone suffixes`() {
        assertEquals("苏州工业园区", LocationNameNormalizer.normalizeAddressDetail("江苏省苏州市苏州工业园区"))
        assertEquals("科技园区", LocationNameNormalizer.normalizeAddressDetail("广东省深圳市南山区科技园区"))
    }

    @Test
    fun `address detail keeps road and landmark detail from full address`() {
        assertEquals("高新南四区腾讯大厦", LocationNameNormalizer.normalizeAddressDetail("广东省深圳市南山区高新南四区腾讯大厦"))
        assertEquals("世纪大道100号环球金融中心", LocationNameNormalizer.normalizeAddressDetail("上海市浦东新区世纪大道100号环球金融中心"))
        assertEquals("高新南路TCL大厦", LocationNameNormalizer.normalizeAddressDetail("广东省深圳市南山区高新南路TCL大厦"))
    }

    @Test
    fun `poi part keeps structured names without administrative stripping`() {
        assertEquals("高新南四区", LocationNameNormalizer.normalizePoiPart("高新南四区"))
        assertEquals("张江路", LocationNameNormalizer.normalizePoiPart("张江路"))
        assertEquals("腾讯大厦", LocationNameNormalizer.normalizePoiPart("腾讯大厦"))
    }

    @Test
    fun `address detail drops pure number addresses`() {
        assertNull(LocationNameNormalizer.normalizeAddressDetail("88号"))
        assertNull(LocationNameNormalizer.normalizePoiPart("12栋"))
    }
}

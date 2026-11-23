package com.example

import com.example.model.UrlValidationResult
import com.example.model.UrlValidator
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun `valid https url passes validation`() {
        val result = UrlValidator.validate("https://example.com/path?x=1")
        assertTrue(result is UrlValidationResult.Valid)
    }

    @Test
    fun `valid http localhost url passes validation`() {
        val result = UrlValidator.validate("http://localhost:8080")
        assertTrue(result is UrlValidationResult.Valid)
    }

    @Test
    fun `non http scheme fails validation`() {
        val result = UrlValidator.validate("javascript:alert(1)")
        assertTrue(result is UrlValidationResult.Invalid)
    }

    @Test
    fun `blank url fails validation`() {
        val result = UrlValidator.validate("   ")
        assertTrue(result is UrlValidationResult.Invalid)
    }
}

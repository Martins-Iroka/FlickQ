package com.martdev.flickq.validation

object Validator {

    fun validateEmail(email: String): ResultValidation {
        val trimmedEmail = email.trim()
        return when {
            trimmedEmail.isBlank() -> ResultValidation.Invalid("Email is required")
            !trimmedEmail.contains("@") -> ResultValidation.Invalid("Email must contain @")
            !trimmedEmail.substringAfter("@").contains(".") -> ResultValidation.Invalid("Email must have a valid domain")
            trimmedEmail.length < 5 -> ResultValidation.Invalid("Email is too short")
            trimmedEmail.contains(" ") -> ResultValidation.Invalid("Email cannot contain spaces")
            else -> ResultValidation.Valid
        }
    }

    fun validatePassword(password: String): ResultValidation {
        val errors = mutableListOf<String>()
        if (password.length < 8) errors.add("At least 8 characters")
        if (password.none { it.isDigit() }) errors.add("At least one number")
        if (password.none { it.isUpperCase() }) errors.add("At least on uppercase")
        if (password.none { it.isLowerCase() }) errors.add("At least one lowercase")
//        if (password.none { !it.isLetterOrDigit() }) errors.add("At least one special character")
        return if (errors.isEmpty()) ResultValidation.Valid else ResultValidation.Invalid(errors.joinToString(". ") + ".")
    }
}

sealed interface ResultValidation {
    data object Valid : ResultValidation
    data class Invalid(val message: String): ResultValidation
    val isValid: Boolean get() = this is Valid
    val errorMessage: String? get() = (this as? Invalid)?.message
}
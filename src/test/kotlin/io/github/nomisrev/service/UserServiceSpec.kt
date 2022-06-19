package io.github.nomisrev.service

import arrow.core.nonEmptyListOf
import io.github.nefilim.kjwt.JWSHMAC512Algorithm
import io.github.nefilim.kjwt.JWT
import io.github.nomisrev.EmptyUpdate
import io.github.nomisrev.IncorrectInput
import io.github.nomisrev.UsernameAlreadyExists
import io.github.nomisrev.InvalidEmail
import io.github.nomisrev.InvalidPassword
import io.github.nomisrev.InvalidUsername
import io.github.nomisrev.KotestProject
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.utils.query
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.FreeSpec

class UserServiceSpec : FreeSpec({

  val dependencies by KotestProject.dependencies
  val userService by lazy { dependencies.userService }

  val validUsername = "username"
  val validEmail = "valid@domain.com"
  val validPw = "123456789"

  afterTest { dependencies.dataSource.query("TRUNCATE users CASCADE") }

  "register" -
    {
      "username cannot be empty" {
        val res = userService.register(RegisterUser("", validEmail, validPw))
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 1 characters)")
        val expected = IncorrectInput(InvalidUsername(errors))
        res shouldBeLeft expected
      }

      "username longer than 25 chars" {
        val name = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val res = userService.register(RegisterUser(name, validEmail, validPw))
        val errors = nonEmptyListOf("is too long (maximum is 25 characters)")
        val expected = IncorrectInput(InvalidUsername(errors))
        res shouldBeLeft expected
      }

      "email cannot be empty" {
        val res = userService.register(RegisterUser(validUsername, "", validPw))
        val errors = nonEmptyListOf("Cannot be blank", "'' is invalid email")
        val expected = IncorrectInput(InvalidEmail(errors))
        res shouldBeLeft expected
      }

      "email too long" {
        val email = "${(0..340).joinToString("") { "A" }}@domain.com"
        val res = userService.register(RegisterUser(validUsername, email, validPw))
        val errors = nonEmptyListOf("is too long (maximum is 350 characters)")
        val expected = IncorrectInput(InvalidEmail(errors))
        res shouldBeLeft expected
      }

      "email is not valid" {
        val email = "AAAA"
        val res = userService.register(RegisterUser(validUsername, email, validPw))
        val errors = nonEmptyListOf("'$email' is invalid email")
        val expected = IncorrectInput(InvalidEmail(errors))
        res shouldBeLeft expected
      }

      "password cannot be empty" {
        val res = userService.register(RegisterUser(validUsername, validEmail, ""))
        val errors = nonEmptyListOf("Cannot be blank", "is too short (minimum is 8 characters)")
        val expected = IncorrectInput(InvalidPassword(errors))
        res shouldBeLeft expected
      }

      "password can be max 100" {
        val password = (0..100).joinToString("") { "A" }
        val res = userService.register(RegisterUser(validUsername, validEmail, password))
        val errors = nonEmptyListOf("is too long (maximum is 100 characters)")
        val expected = IncorrectInput(InvalidPassword(errors))
        res shouldBeLeft expected
      }

      "All valid returns a token" {
        userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
      }

      "Register twice results in" {
        userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
        val res = userService.register(RegisterUser(validUsername, validEmail, validPw))
        res shouldBeLeft UsernameAlreadyExists(validUsername)
      }
    }

  "update" -
    {
      "Update with all null" {
        val token =
          userService.register(RegisterUser(validUsername, validEmail, validPw)).shouldBeRight()
        val res = userService.update(Update(token.id(), null, null, null, null, null))
        res shouldBeLeft
          EmptyUpdate("Cannot update user with ${token.id()} with only null values")
      }
    }
})

private fun JwtToken.id(): UserId =
  JWT
    .decodeT(value, JWSHMAC512Algorithm)
    .shouldBeRight { "JWToken $value should be valid JWT but found $it" }
    .jwt
    .claimValueAsLong("id")
    .shouldBeSome { "JWTToken $value should have id but found None" }
    .let(::UserId)

package org.codehaus.groovy.grails.web.binding.json

import grails.artefact.Artefact
import grails.test.mixin.TestFor

import org.codehaus.groovy.grails.web.binding.bindingsource.JsonDataBindingSourceCreator.JsonObjectMap

import spock.lang.Specification

@TestFor(BindingController)
class JsonBindingSpec extends Specification {

    void 'Test binding JSON body'() {
        given:
        request.json = '''
            {
    "name": "Douglas", "age": "42"}
'''
    when:
        def model = controller.createPersonCommandObject()
    then:
        model.person instanceof Person
        model.person.name == 'Douglas'
        model.person.age == 42
    }

    void 'Test binding nested collection elements'() {
        given:
        request.json = '''
            {
    "lastName": "Brown",
    "familyMembers": [
        {"name": "Jake", "age": "12"},
        {"name": "Zack", "age": "15"}
    ]
}
'''
    when:
        def model = controller.createFamily()
    then:
        model.family.lastName == 'Brown'

        model.family.familyMembers.size() == 2

        model.family.familyMembers[0].name == 'Jake'
        model.family.familyMembers[0].age == 12

        model.family.familyMembers[1].name == 'Zack'
        model.family.familyMembers[1].age == 15
    }

    void 'Test parsing invalid JSON'() {
        given:
        request.json = '''
            {
    "name": [foo.[} this is unparseable JSON{[
'''
        when:
        def model = controller.createPersonCommandObject()

        then:
        response.status == 400
        model == null

        when:
        request.json = '''
            {
    "name": [foo.[} this is unparseable JSON{[
'''
        model = controller.createPerson()
        def person = model.person

        then:
        person.hasErrors()
        person.errors.errorCount == 1
        person.errors.allErrors[0].defaultMessage == 'An error occurred parsing the body of the request'
        person.errors.allErrors[0].code == 'invalidRequestBody'
        'invalidRequestBody' in person.errors.allErrors[0].codes
        'org.codehaus.groovy.grails.web.binding.json.Person.invalidRequestBody' in person.errors.allErrors[0].codes
    }
    
    void 'Test parsing JSON with other than UTF-8 content type'() {
        given:
            String jsonString = '{"name":"Hello öäåÖÄÅ"}'
            request.contentType = 'application/json; charset=UTF-16'
            request.content = jsonString.getBytes("UTF-16")
        when:
            def model = controller.createPersonCommandObject()
        then:
            model.person instanceof Person
            model.person.name == 'Hello öäåÖÄÅ'
    }
    
    void 'Test binding JSON to a Map'() {
        given:
        request.contentType = JSON_CONTENT_TYPE
        request.method = 'POST'
        request.JSON = '{"mapData": {"name":"Jeff", "country":"USA"}}'
        
        when:
        def model = controller.createFamily()
        
        then:
        model.family.mapData instanceof Map
        !(model.family.mapData instanceof JsonObjectMap)
        model.family.mapData.name == 'Jeff'
        model.family.mapData.country == 'USA'
    }
}

@Artefact('Controller')
class BindingController {
    def createPerson() {
        def person = new Person()
        person.properties = request
        [person: person]
    }

    def createPersonCommandObject(Person person) {
        [person: person]
    }

    def createFamily(Family family) {
        [family: family]
    }
}

class Person {
    String name
    Integer age
}

class Family {
    String lastName
    List<Person> familyMembers
    Map mapData
}

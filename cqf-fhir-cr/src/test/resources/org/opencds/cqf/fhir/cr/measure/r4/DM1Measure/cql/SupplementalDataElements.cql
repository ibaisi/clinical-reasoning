library SupplementalDataElements version '1.0.1'

using FHIR version '4.0.1'

include DQMDiabetesFHIRHelpers version '1.0.1' called FHIRHelpers
include FHIRCommon version '1.0.1' called FC
include DQMDiabetesCommonLogic version '1.0.1' called Common
include AmbulatoryVisits version '1.0.1' called AV

valueset "Ethnicity": 'http://cds.optum.com/dqm-diabetes/fhir/ValueSet/2.16.840.1.114222.4.11.837' version '1.0.1'
valueset "ONC Administrative Sex": 'http://cds.optum.com/dqm-diabetes/fhir/ValueSet/2.16.840.1.113762.1.4.1' version '1.0.1'
valueset "Payer": 'http://cds.optum.com/dqm-diabetes/fhir/ValueSet/2.16.840.1.114222.4.11.3591' version '1.0.1'
valueset "Race": 'http://cds.optum.com/dqm-diabetes/fhir/ValueSet/2.16.840.1.114222.4.11.836' version '1.0.1'

context Patient

define "SDE Ethnicity":
  (flatten (
      Patient.extension Extension
        where Extension.url = 'http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity'
          return Extension.extension
    )) E
      where E.url = 'ombCategory'
        or E.url = 'detailed'
      return E.value as Coding

define "SDE Payer":
  [Coverage: type in "Payer"] Payer
        return {
          code: Payer.type,
          period: Payer.period
        }

define "SDE Race":
  (flatten (
      Patient.extension Extension
        where Extension.url = 'http://hl7.org/fhir/us/core/StructureDefinition/us-core-race'
          return Extension.extension
    )) E
      where E.url = 'ombCategory'
        or E.url = 'detailed'
      return E.value as Coding

define "SDE Sex":
  case
      when Patient.gender = 'male' then Code { code: 'M', system: 'http://hl7.org/fhir/v3/AdministrativeGender', display: 'Male' }
      when Patient.gender = 'female' then Code { code: 'F', system: 'http://hl7.org/fhir/v3/AdministrativeGender', display: 'Female' }
      else null
    end


// SDEs: Provider, Days since last appointment, Last Appointment date, A1c Value
define "Last Appointment":
  (Common.MostRecentEncounter(AV."Qualifying Encounters"))

define "Last Appointment Date":
  date from end of "Last Appointment".period

define "Most Recent Practitioner ID":
  "Last Appointment" QualifyingEncounter
    return (
      QualifyingEncounter.participant.individual Participant
        where Participant.type = 'Practitioner'
      or Common.GetRelativeType(Participant.reference) = 'Practitioner'
    )

define "Most Recent Practitioner Name":
  First(
    [Practitioner] P
      with "Most Recent Practitioner ID" ID
        such that P.id ~ Common.GetId(ID.reference)
    return First(P.name.given) + ' ' + First(P.name.family)
  )

define "Most Recent Practitioner":
  First([Practitioner] P
    with "Most Recent Practitioner ID" ID
      such that P.id ~ Common.GetId(ID.reference))

define "Most Recent Practitioner Primitive":
if "Most Recent Practitioner Name" is null then 'None' else "Most Recent Practitioner Name"

define "Days Since Last Appointment":
  ToString(Abs(difference in days between end of "Last Appointment".period and ToDateTime(Today()))) + ' days'

// Filters: Provider: Chosen from list of providers (CQL Definition to create list of providers -- not exposed as SDE)
//          A1c: Whole Number filters from 1 - 15
define "Patient General Practitioners":
  Patient.generalPractitioner GP
    where GP.type = 'Practitioner'
      or Common.GetRelativeType(GP.reference) = 'Practitioner'

define "Qualifying Encounter Practitioners":
  flatten(AV."Qualifying Encounters" QualifyingEncounter
    return (
      QualifyingEncounter.participant.individual Participant
        where Participant.type = 'Practitioner'
      or Common.GetRelativeType(Participant.reference) = 'Practitioner'
    )
  )

define "Patient Practitioner List":
  flatten(
    { 
      "Patient General Practitioners"
      union "Qualifying Encounter Practitioners" 
    }
  ) except { null }

define "Practitioners List":
  [Practitioner] P
    //return First(P.name.given) + ' ' + First(P.name.family)


// Tests
define "Resource Test":
  [Patient]

define "Provider Test":
  First([Practitioner] P where P.id = 'DM1-practitioner-4')

define "Tuple Test - Provider":
  First([Practitioner] P where P.id = 'DM1-practitioner-5')

define "Tuple Test - Provider Name":
  "Tuple Test - Provider" P
    return First(P.name.given) + ' ' + First(P.name.family)

define "Tuple Test":
  Tuple { 
    Resource: "Tuple Test - Provider",
    ResourceValue: "Tuple Test - Provider Name",
    ResourceID: "Tuple Test - Provider".id.value
  }

define "List Test":
  [Practitioner]

define "Resource Construction Test":
  FHIR.Observation {
    // id: FHIR.id { id: 'observation-sdetest-' + ReplaceMatches(ToString(Now()), ':', '') },
    id: FHIR.id { id: 'observation-sdetest-' + ToString(Now()) },
    status: FHIR.ObservationStatus { value: 'final' },
    code: FHIR.CodeableConcept { coding: { FHIR.Coding { code: FHIR.code { value: 'days-since-appointment' } } } },
    subject: FHIR.Reference { reference: FHIR.string { value: Patient.id.value }},
    value: FHIR.integer { value: Abs(difference in days between end of "Last Appointment".period and ToDateTime(Today())) }
  }

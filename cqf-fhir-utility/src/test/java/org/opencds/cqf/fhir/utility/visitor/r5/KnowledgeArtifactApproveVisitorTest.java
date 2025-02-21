package org.opencds.cqf.fhir.utility.visitor.r5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.opencds.cqf.fhir.utility.r5.Parameters.parameters;
import static org.opencds.cqf.fhir.utility.r5.Parameters.part;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import java.util.Date;
import java.util.Optional;
import org.hl7.fhir.r5.model.ArtifactAssessment;
import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.CanonicalType;
import org.hl7.fhir.r5.model.CodeType;
import org.hl7.fhir.r5.model.DateType;
import org.hl7.fhir.r5.model.IdType;
import org.hl7.fhir.r5.model.Library;
import org.hl7.fhir.r5.model.MarkdownType;
import org.hl7.fhir.r5.model.Parameters;
import org.hl7.fhir.r5.model.Practitioner;
import org.hl7.fhir.r5.model.Reference;
import org.hl7.fhir.r5.model.RelatedArtifact.RelatedArtifactType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opencds.cqf.fhir.api.Repository;
import org.opencds.cqf.fhir.utility.adapter.LibraryAdapter;
import org.opencds.cqf.fhir.utility.adapter.r5.AdapterFactory;
import org.opencds.cqf.fhir.utility.repository.InMemoryFhirRepository;
import org.opencds.cqf.fhir.utility.visitor.KnowledgeArtifactApproveVisitor;

class KnowledgeArtifactApproveVisitorTest {
    private final FhirContext fhirContext = FhirContext.forR5Cached();
    private Repository spyRepository;
    private final IParser jsonParser = fhirContext.newJsonParser();

    @BeforeEach
    void setup() {
        var lib = (Library) jsonParser.parseResource(
                KnowledgeArtifactReleaseVisitorTests.class.getResourceAsStream("Library-ersd-active.json"));
        spyRepository = spy(new InMemoryFhirRepository(fhirContext));
        spyRepository.update(lib);
        doAnswer(new Answer<Bundle>() {
                    @Override
                    public Bundle answer(InvocationOnMock a) throws Throwable {
                        Bundle b = a.getArgument(0);
                        return InMemoryFhirRepository.transactionStub(b, spyRepository);
                    }
                })
                .when(spyRepository)
                .transaction(any());
    }

    @Test
    void approveOperation_endpoint_id_should_match_target_parameter() {
        var artifactAssessmentTarget = "Library/This-Library-Does-Not-Exist|1.0.0";
        var params = parameters(part("artifactAssessmentTarget", new CanonicalType(artifactAssessmentTarget)));
        UnprocessableEntityException maybeException = null;
        var releaseVisitor = new KnowledgeArtifactApproveVisitor();
        var lib = spyRepository
                .read(Library.class, new IdType("Library/SpecificationLibrary"))
                .copy();
        var libraryAdapter = new AdapterFactory().createLibrary(lib);

        try {
            libraryAdapter.accept(releaseVisitor, spyRepository, params);
        } catch (UnprocessableEntityException e) {
            maybeException = e;
        }
        assertNotNull(maybeException);
        assertTrue(maybeException.getMessage().contains("URL"));
        maybeException = null;
        artifactAssessmentTarget = "http://hl7.org/fhir/us/ecr/Library/SpecificationLibrary|this-version-is-wrong";
        params = parameters(part("artifactAssessmentTarget", new CanonicalType(artifactAssessmentTarget)));
        try {
            libraryAdapter.accept(releaseVisitor, spyRepository, params);
        } catch (UnprocessableEntityException e) {
            maybeException = e;
        }
        assertNotNull(maybeException);
        assertTrue(maybeException.getMessage().contains("version"));
    }

    @Test
    void approveOperation_should_respect_artifactAssessment_information_type_binding() {
        String artifactAssessmentType = "this-type-does-not-exist";
        Parameters params = parameters(part("artifactAssessmentType", new CodeType(artifactAssessmentType)));
        UnprocessableEntityException maybeException = null;
        KnowledgeArtifactApproveVisitor releaseVisitor = new KnowledgeArtifactApproveVisitor();
        Library lib = spyRepository
                .read(Library.class, new IdType("Library/SpecificationLibrary"))
                .copy();
        LibraryAdapter libraryAdapter = new AdapterFactory().createLibrary(lib);

        try {
            libraryAdapter.accept(releaseVisitor, spyRepository, params);
        } catch (UnprocessableEntityException e) {
            maybeException = e;
        }
        assertNotNull(maybeException);
    }

    @Test
    void approveOperation_test() {
        var practitioner = (Practitioner) jsonParser.parseResource(
                KnowledgeArtifactReleaseVisitorTests.class.getResourceAsStream("Practitioner-minimal.json"));
        spyRepository.update(practitioner);
        Date today = new Date();
        // get today's date in the form "2023-05-11"
        DateType approvalDate = new DateType(today, TemporalPrecisionEnum.DAY);
        String artifactAssessmentType = "comment";
        String artifactAssessmentSummary = "comment text";
        String artifactAssessmentTarget = "http://hl7.org/fhir/us/ecr/Library/SpecificationLibrary|1.0.0";
        String artifactAssessmentRelatedArtifact = "reference-valid-no-spaces";
        String artifactAssessmentAuthor = "Practitioner/sample-practitioner";
        Parameters params = parameters(
                part("approvalDate", approvalDate),
                part("artifactAssessmentType", new CodeType(artifactAssessmentType)),
                part("artifactAssessmentSummary", new MarkdownType(artifactAssessmentSummary)),
                part("artifactAssessmentTarget", new CanonicalType(artifactAssessmentTarget)),
                part("artifactAssessmentRelatedArtifact", new CanonicalType(artifactAssessmentRelatedArtifact)),
                part("artifactAssessmentAuthor", new Reference(artifactAssessmentAuthor)));
        KnowledgeArtifactApproveVisitor approveVisitor = new KnowledgeArtifactApproveVisitor();
        Library lib = spyRepository
                .read(Library.class, new IdType("Library/SpecificationLibrary"))
                .copy();
        LibraryAdapter libraryAdapter = new AdapterFactory().createLibrary(lib);
        Bundle returnedResource = (Bundle) libraryAdapter.accept(approveVisitor, spyRepository, params);

        assertNotNull(returnedResource);
        Library approvedLibrary = spyRepository
                .read(Library.class, new IdType("Library/SpecificationLibrary"))
                .copy();
        assertNotNull(approvedLibrary);
        // Ensure Approval Date matches input parameter
        assertEquals(approvedLibrary.getApprovalDateElement().asStringValue(), approvalDate.asStringValue());
        // Ensure that approval date is NOT before Library.date (see $release)
        assertFalse(approvedLibrary.getApprovalDate().before(approvedLibrary.getDate()));
        // ArtifactAssessment is saved as type Basic, update when we change to OperationOutcome
        // Get the reference from BundleEntry.response.location
        Optional<BundleEntryComponent> maybeArtifactAssessment = returnedResource.getEntry().stream()
                .filter(entry -> entry.getResponse().getLocation().contains("ArtifactAssessment"))
                .findAny();
        assertTrue(maybeArtifactAssessment.isPresent());
        var artifactAssessment = spyRepository.read(
                ArtifactAssessment.class,
                new IdType(maybeArtifactAssessment.get().getResponse().getLocation()));
        assertNotNull(artifactAssessment);
        assertTrue(isValidArtifactComment(artifactAssessment));
        assertTrue(checkArtifactCommentParams(
                artifactAssessment,
                artifactAssessmentType,
                artifactAssessmentSummary,
                "Library/SpecificationLibrary",
                artifactAssessmentRelatedArtifact,
                artifactAssessmentTarget,
                artifactAssessmentAuthor));
    }

    private boolean checkArtifactCommentParams(
            ArtifactAssessment assessment,
            String artifactAssessmentType,
            String artifactAssessmentSummary,
            String artifactAssessmentTargetReference,
            String artifactAssessmentRelatedArtifact,
            String derivedFromRelatedArtifactUrl,
            String artifactAssessmentAuthor) {
        boolean infoTypeCorrect = false;
        boolean summaryCorrect = false;
        boolean citationRelatedArtifactCorrect = false;
        boolean derivedFromRelatedArtifactCorrect = false;
        boolean authorCorrect = false;
        boolean artifactCorrect = false;
        var content = assessment.getContentFirstRep();
        infoTypeCorrect = content.getInformationType().toCode().equals(artifactAssessmentType);
        summaryCorrect = content.getSummary().equals(artifactAssessmentSummary);
        derivedFromRelatedArtifactCorrect = content.getRelatedArtifact().stream()
                .filter(ra -> ra.getType() == RelatedArtifactType.DERIVEDFROM
                        && ra.getResource().equals(derivedFromRelatedArtifactUrl))
                .findFirst()
                .isPresent();
        citationRelatedArtifactCorrect = content.getRelatedArtifact().stream()
                .filter(ra -> ra.getType() == RelatedArtifactType.CITATION
                        && ra.getResource().equals(artifactAssessmentRelatedArtifact))
                .findFirst()
                .isPresent();
        authorCorrect = content.getAuthor().getReference().equals(artifactAssessmentAuthor);
        artifactCorrect = assessment.getArtifactReference().getReference().equals(artifactAssessmentTargetReference);
        return artifactCorrect
                && infoTypeCorrect
                && summaryCorrect
                && citationRelatedArtifactCorrect
                && derivedFromRelatedArtifactCorrect
                && authorCorrect;
    }

    private boolean isValidArtifactComment(ArtifactAssessment assessment) {
        if (!assessment.hasContent()) {
            return false;
        } else {
            var content = assessment.getContentFirstRep();
            boolean infoTypeExists = content.hasInformationType();
            boolean summaryExists = content.hasSummary();
            boolean relatedArtifactExists = content.hasRelatedArtifact();
            boolean authorExists = content.hasAuthor();
            boolean dateExists = assessment.hasDate();
            boolean artifactExists = assessment.hasArtifact();
            return (infoTypeExists || summaryExists || relatedArtifactExists || authorExists)
                    && dateExists
                    && artifactExists;
        }
    }
}

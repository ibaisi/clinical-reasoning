package org.opencds.cqf.fhir.utility.visitor.r4;

import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.MetadataResource;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.RelatedArtifact;
import org.hl7.fhir.r4.model.UsageContext;
import org.opencds.cqf.fhir.api.Repository;
import org.opencds.cqf.fhir.utility.SearchHelper;

public class KnowledgeArtifactDraftVisitor {

    public static Optional<MetadataResource> processReferencedResourceForDraft(
            Repository repository, RelatedArtifact ra, String version) {
        Optional<MetadataResource> referencedResource = Optional.empty();
        if (ra.hasUrl() || ra.hasResource()) {
            Bundle referencedResourceBundle;
            if (ra.hasUrl()) {
                referencedResourceBundle = (Bundle)
                        SearchHelper.searchRepositoryByCanonicalWithPaging(repository, new CanonicalType(ra.getUrl()));
            } else {
                referencedResourceBundle = (Bundle)
                        SearchHelper.searchRepositoryByCanonicalWithPaging(repository, ra.getResourceElement());
            }
            if (!referencedResourceBundle.getEntryFirstRep().isEmpty()) {
                var referencedResourceEntry = referencedResourceBundle.getEntryFirstRep();
                if (referencedResourceEntry.hasResource()
                        && referencedResourceEntry.getResource() instanceof MetadataResource) {
                    referencedResource = Optional.of((MetadataResource) referencedResourceEntry.getResource());
                }
            }
        }
        return referencedResource;
    }

    public static void updateUsageContextReferencesWithUrns(
            MetadataResource newResource,
            List<MetadataResource> resourceListWithOriginalIds,
            List<IdType> idListForTransactionBundle) {
        List<UsageContext> useContexts = newResource.getUseContext();
        for (UsageContext useContext : useContexts) {
            // TODO: will we ever need to resolve these references?
            if (useContext.hasValueReference()) {
                Reference useContextRef = useContext.getValueReference();
                if (useContextRef != null) {
                    resourceListWithOriginalIds.stream()
                            .filter(resource -> (resource.getClass().getSimpleName() + "/"
                                            + resource.getIdElement().getIdPart())
                                    .equals(useContextRef.getReference()))
                            .findAny()
                            .ifPresent(resource -> {
                                int indexOfDraftInIdList = resourceListWithOriginalIds.indexOf(resource);
                                useContext.setValue(
                                        new Reference(idListForTransactionBundle.get(indexOfDraftInIdList)));
                            });
                }
            }
        }
    }
}

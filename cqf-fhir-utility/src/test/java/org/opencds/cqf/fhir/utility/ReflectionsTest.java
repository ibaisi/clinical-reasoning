package org.opencds.cqf.fhir.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ca.uhn.fhir.context.BaseRuntimeChildDefinition.IAccessor;
import java.util.Optional;
import java.util.function.Function;
import org.hl7.fhir.dstu3.model.Library;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.junit.jupiter.api.Test;

class ReflectionsTest {

    @Test
    void accessor() {
        Library library = new Library().setName("test");

        IAccessor accessor = Reflections.getAccessor(library.getClass(), "name");

        Optional<IBase> opt = accessor.getFirstValueOrNull(library);

        @SuppressWarnings("unchecked")
        IPrimitiveType<String> value = (IPrimitiveType<String>) opt.get();
        assertEquals("test", value.getValue());
    }

    @Test
    void getName() {
        Library library = new Library().setName("test");

        Function<Library, String> getName = Reflections.getNameFunction(library.getClass());

        String name = getName.apply(library);

        assertEquals("test", name);
    }

    @Test
    void getNameLiteral() {
        Library library = new Library().setName("test");

        Function<Library, String> getName = Reflections.getNameFunction(Library.class);

        String name = getName.apply(library);

        assertEquals("test", name);
    }

    @Test
    void getNameNotExists() {
        assertThrows(RuntimeException.class, () -> {
            Reflections.getNameFunction(Observation.class);
        });
    }

    @Test
    void getVersion() {
        Library library = new Library().setVersion("test");

        Function<Library, String> getVersion = Reflections.getVersionFunction(library.getClass());

        String version = getVersion.apply(library);

        assertEquals("test", version);
    }

    @Test
    void getUrl() {
        Library library = new Library().setUrl("http://test.com");

        Function<Library, String> getVersion = Reflections.getUrlFunction(library.getClass());

        String version = getVersion.apply(library);

        assertEquals("http://test.com", version);
    }
}

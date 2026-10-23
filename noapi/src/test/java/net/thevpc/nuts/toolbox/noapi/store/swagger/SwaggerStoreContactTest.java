package net.thevpc.nuts.toolbox.noapi.store.swagger;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.elem.NElement;
import net.thevpc.nuts.elem.NObjectElementBuilder;
import net.thevpc.nuts.toolbox.noapi.model.MContact;
import net.thevpc.nuts.toolbox.noapi.model.MStoreModel;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The contact of the introduction section is read from {@code info.contact}: the name, the email
 * and the url are three distinct fields.
 */
public class SwaggerStoreContactTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static NElement document(String... contactMembers) {
        NObjectElementBuilder contact = NObjectElementBuilder.of();
        for (int i = 0; i < contactMembers.length; i += 2) {
            contact.add(contactMembers[i], NElement.ofString(contactMembers[i + 1]));
        }
        return NObjectElementBuilder.of()
                .add("info", NObjectElementBuilder.of()
                        .add("contact", contact.build())
                        .build())
                .build();
    }

    @Test
    public void contactFieldsAreReadFromTheirOwnKeys() {
        MContact contact = new SwaggerStore()
                .getContact(document(
                        "name", "Avidea - FTUSA integration support",
                        "email", "support@avidea.com",
                        "url", "https://ftusa-extranet.com/api"))
                .orElse(null);
        assertNotNull(contact);
        assertEquals("Avidea - FTUSA integration support", contact.name);
        assertEquals("support@avidea.com", contact.email);
        assertEquals("https://ftusa-extranet.com/api", contact.url);
    }

    @Test
    public void missingEmailIsEmpty() {
        MContact contact = new SwaggerStore()
                .getContact(document("name", "Avidea"))
                .orElse(null);
        assertNotNull(contact);
        assertEquals("Avidea", contact.name);
        assertEquals("", contact.email);
        assertEquals("", contact.url);
    }

    @Test
    public void missingContactIsNull() {
        NElement doc = NObjectElementBuilder.of()
                .add("info", NObjectElementBuilder.of().build())
                .build();
        assertNull(new SwaggerStore().getContact(doc).orElse(null));
    }

    @Test
    public void storeModelExposesTheContact() {
        NElement doc = NObjectElementBuilder.of()
                .add("info", NObjectElementBuilder.of()
                        .add("contact", NObjectElementBuilder.of()
                                .add("name", NElement.ofString("Avidea"))
                                .add("email", NElement.ofString("support@avidea.com"))
                                .build())
                        .build())
                .add("custom", NObjectElementBuilder.of()
                        .add("openapi-document-id", NElement.ofString("ftusa-api"))
                        .build())
                .build();
        MStoreModel model = new SwaggerStore().loadStoreModel(doc);
        assertNotNull(model.getContact());
        assertEquals("Avidea", model.getContact().name);
        assertEquals("support@avidea.com", model.getContact().email);
    }
}

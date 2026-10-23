package net.thevpc.nuts.toolbox.noapi.util;

import net.thevpc.nuts.Nuts;
import net.thevpc.nuts.lib.md.MdCode;
import net.thevpc.nuts.lib.md.MdElement;
import net.thevpc.nuts.toolbox.noapi.model.TypeInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The type of a field is rendered in the tables of the documentation: a referenced schema is
 * rendered as a code, like the other types, and it keeps the required marker.
 */
public class NoApiUtilsTest {

    @BeforeClass
    public static void boot() {
        Nuts.require();
    }

    private static AppMessages messages() {
        return new AppMessages(null,
                NoApiUtilsTest.class.getResource("/net/thevpc/nuts/toolbox/noapi/messages-en.json"));
    }

    private static TypeInfo refType(String ref) {
        TypeInfo type = new TypeInfo();
        type.setType("ref");
        type.setUserType("$ref");
        type.setRef(ref);
        return type;
    }

    @Test
    public void referencedTypeKeepsTheRequiredMarker() {
        MdElement element = NoApiUtils.codeElementTypeInfo(refType("LoginTokens"), false, " [REQUIRED]", messages());
        assertTrue("a referenced type is rendered as a code: " + element, element instanceof MdCode);
        assertEquals("LoginTokens [REQUIRED]", ((MdCode) element).getValue());
    }

    @Test
    public void referencedTypeWithoutMarker() {
        MdElement element = NoApiUtils.codeElementTypeInfo(refType("VehicleInsuranceStatus"), false, "", messages());
        assertTrue("a referenced type is rendered as a code: " + element, element instanceof MdCode);
        assertEquals("VehicleInsuranceStatus", ((MdCode) element).getValue());
    }
}

/*
 * Copyright 2001-2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.xkms2.builder;

import java.util.Iterator;

import org.apache.axiom.om.OMElement;
import org.wso2.xkms2.KeyBinding;
import org.wso2.xkms2.PrivateKey;
import org.wso2.xkms2.RegisterResult;
import org.wso2.xkms2.XKMS2Constants;
import org.wso2.xkms2.XKMSElement;
import org.wso2.xkms2.XKMSException;

public class RegisterResultBuilder extends ResultTypeBuilder {

    public static final RegisterResultBuilder INSTANCE = new RegisterResultBuilder();

    private RegisterResultBuilder() {
    }

    public XKMSElement buildElement(OMElement element) throws XKMSException {
        RegisterResult registerResult = new RegisterResult();
        super.buildElement(element, registerResult);

        OMElement keyBindingElem;
        for (Iterator iterator = element
                .getChildrenWithName(XKMS2Constants.ELE_KEY_BINDING); iterator
                .hasNext();) {
            keyBindingElem = (OMElement) iterator.next();
            registerResult
                    .addKeyBinding((KeyBinding) KeyBindingBuilder.INSTANCE
                            .buildElement(keyBindingElem));
        }

        OMElement privateKeyElem = element
                .getFirstChildWithName(XKMS2Constants.Q_ELEM_PRIVATE_KEY);
        if (privateKeyElem != null) {
            PrivateKey privateKey = (PrivateKey) PrivateKeyBuilder.INSTANCE.buildElement(privateKeyElem);
            registerResult.setPrivateKey(privateKey);
        }

        return registerResult;
    }

}

/*
 * LDAP Chai API
 * Copyright (c) 2006-2017 Novell, Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.novell.ldapchai.cr;

import org.jrivard.xmlchai.XmlElement;
import com.novell.ldapchai.cr.bean.AnswerBean;
import com.novell.ldapchai.exception.ChaiOperationException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An {@code Answer} is an instance of a response to a challenge/response question.
 */
public interface Answer
{
    boolean testAnswer( String answer );

    XmlElement toXml()
            throws ChaiOperationException;

    AnswerBean asAnswerBean();

    /**
     * Defined format types for answers.  Format types typically denote the hash mechanism (if any)
     * and any other character encoding and possibly the storage system.
     */
    enum FormatType
    {
        TEXT( new TextAnswer.TextAnswerFactory(), 0, 0 ),
        MD5( new HashSaltAnswer.HashSaltAnswerFactory(), 0, 10_000_000 ),
        SHA1( new HashSaltAnswer.HashSaltAnswerFactory(), 0, 10_000_000 ),
        SHA1_SALT( new HashSaltAnswer.HashSaltAnswerFactory(), 32, 10_000_000 ),
        SHA256_SALT( new HashSaltAnswer.HashSaltAnswerFactory(), 32, 5_000_000 ),
        SHA512_SALT( new HashSaltAnswer.HashSaltAnswerFactory(), 32, 5_000_000 ),
        BCRYPT( new PasswordCryptAnswer.PasswordCryptAnswerFactory(), 16, 15 ),
        SCRYPT( new PasswordCryptAnswer.PasswordCryptAnswerFactory(), 32, 16 * 1024 ),
        PBKDF2( new PBKDF2Answer.PKDBF2AnswerFactory(), 32, 1_000_000 ),
        PBKDF2_SHA256( new PBKDF2Answer.PKDBF2AnswerFactory(), 32, 1_000_000 ),
        PBKDF2_SHA512( new PBKDF2Answer.PKDBF2AnswerFactory(), 32, 1_000_000 ),
        HELPDESK( new ChaiHelpdeskAnswer.ChaiHelpdeskAnswerFactory(), 0, 0 ),
        NMAS( null, -1, -1 ),;

        private final transient ImplementationFactory factory;
        private final int saltLength;
        private final int defaultIterations;

        FormatType( final ImplementationFactory implementationClass, final int saltLength, final int defaultIterations )
        {
            this.factory = implementationClass;
            this.saltLength = saltLength;
            this.defaultIterations = defaultIterations;
        }

        public ImplementationFactory getFactory()
        {
            return factory;
        }

        public int getDefaultIterations()
        {
            return defaultIterations;
        }

        public int getSaltLength()
        {
            return saltLength;
        }

        public static List<FormatType> implementedValues()
        {
            return Arrays.stream( values() )
                    .filter( formatType -> formatType.getFactory() != null )
                    .collect( Collectors.collectingAndThen( Collectors.toList(), Collections::unmodifiableList ) );
        }
    }

    /**
     * Interface for any factory class that can instantiate an {@link Answer}.
     */
    interface ImplementationFactory
    {
        Answer newAnswer( AnswerConfiguration answerConfiguration, String answerText );

        Answer fromAnswerBean( AnswerBean input, String challengeText );

        Answer fromXml( XmlElement element, boolean caseInsensitive, String challengeText );
    }


}

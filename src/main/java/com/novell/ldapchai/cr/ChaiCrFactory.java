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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.bean.AnswerBean;
import com.novell.ldapchai.cr.bean.ChallengeBean;
import com.novell.ldapchai.exception.ChaiError;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.util.ChaiLogger;

import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Factory for generating {@code Challenge}s, {@code ChallengeSet}s and {@code ResponseSet}s.
 *
 * @author Jason D. Rivard
 */
public final class ChaiCrFactory
{


    /**
     * Constant used to indicate user supplied question.
     */
    public static final String USER_SUPPLIED_QUESTION = "%user%";

    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private static final ChaiLogger LOGGER = ChaiLogger.getLogger( ChaiCrFactory.class );

    /**
     * Create a new ResponseSet.  The generated ResponseSet will be suitable for writing to the directory.
     *
     * @param challengeResponseMap  A map containing Challenges as the key, and string responses for values
     * @param locale                The locale the response set is stored in
     * @param minimumRandomRequired Minimum random responses required
     * @param chaiConfiguration     Appropriate configuration to use during this operation
     * @param csIdentifier          Identifier to store on generated ChaiResponseSet
     * @return A ResponseSet suitable for writing.
     * @throws com.novell.ldapchai.exception.ChaiValidationException when there is a logical problem with the response set data, such as more randoms required then exist
     */
    public static ChaiResponseSet newChaiResponseSet(
            final Map<Challenge, String> challengeResponseMap,
            final Locale locale,
            final int minimumRandomRequired,
            final ChaiConfiguration chaiConfiguration,
            final String csIdentifier
    )
            throws ChaiValidationException
    {
        return newChaiResponseSet( challengeResponseMap, Collections.emptyMap(), locale, minimumRandomRequired, chaiConfiguration, csIdentifier );
    }

    public static ChaiResponseSet newChaiResponseSet(
            final Map<Challenge, String> challengeResponseMap,
            final Map<Challenge, String> helpdeskChallengeResponseMap,
            final Locale locale,
            final int minimumRandomRequired,
            final ChaiConfiguration chaiConfiguration,
            final String csIdentifier
    )
            throws ChaiValidationException
    {
        final boolean caseInsensitive = chaiConfiguration.getBooleanSetting( ChaiSetting.CR_CASE_INSENSITIVE );
        validateAnswers( challengeResponseMap, chaiConfiguration );
        final Map<Challenge, Answer> answerMap = makeAnswerMap( challengeResponseMap, chaiConfiguration );
        final Map<Challenge, HelpdeskAnswer> helpdeskAnswerMap = makeHelpdeskAnswerMap( helpdeskChallengeResponseMap, chaiConfiguration );
        return new ChaiResponseSet( answerMap, helpdeskAnswerMap, locale, minimumRandomRequired, AbstractResponseSet.STATE.NEW, caseInsensitive, csIdentifier, new Date() );
    }

    public static ChaiResponseSet newChaiResponseSet(
            final Collection<ChallengeBean> challengeResponses,
            final Collection<ChallengeBean> helpdeskChallengeResponses,
            final Locale locale,
            final int minimumRandomRequired,
            final String csIdentifier
    )
            throws ChaiValidationException
    {
        final Map<Challenge, Answer> tempCrMap = new LinkedHashMap<>();
        final boolean caseInsensitive = challengeResponses.isEmpty() ? false : challengeResponses.iterator().next().getAnswer().isCaseInsensitive();
        for ( final ChallengeBean challengeBean : challengeResponses )
        {
            final AnswerBean answerBean = challengeBean.getAnswer();
            final Challenge challenge = ChaiChallenge.fromChallengeBean( challengeBean );
            tempCrMap.put( challenge, AnswerFactory.fromAnswerBean( answerBean, challenge.getChallengeText() ) );
            if ( answerBean.isCaseInsensitive() != caseInsensitive )
            {
                throw new IllegalArgumentException( "all answers must have the same caseInsensitive value" );
            }
        }
        final Map<Challenge, HelpdeskAnswer> tempHelpdeskCrMap = new LinkedHashMap<Challenge, HelpdeskAnswer>();
        for ( final ChallengeBean challengeBean : helpdeskChallengeResponses )
        {
            final AnswerBean answerBean = challengeBean.getAnswer();
            final Challenge challenge = ChaiChallenge.fromChallengeBean( challengeBean );
            tempHelpdeskCrMap.put( challenge, ( HelpdeskAnswer ) AnswerFactory.fromAnswerBean( answerBean, challenge.getChallengeText() ) );
        }

        return new ChaiResponseSet( tempCrMap, tempHelpdeskCrMap, locale, minimumRandomRequired, AbstractResponseSet.STATE.NEW, caseInsensitive, csIdentifier, new Date() );
    }

    public static void writeChaiResponseSet(
            final ChaiResponseSet chaiResponseSet,
            final Writer writer
    )
            throws ChaiOperationException
    {
        chaiResponseSet.write( writer );
    }

    public static boolean writeChaiResponseSet(
            final ChaiResponseSet chaiResponseSet,
            final ChaiUser chaiUser
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        return chaiResponseSet.write( chaiUser );
    }

    private static Map<Challenge, HelpdeskAnswer> makeHelpdeskAnswerMap(
            final Map<Challenge, String> crMap,
            final ChaiConfiguration chaiConfiguration
    )
    {
        final Map<Challenge, Answer> tempMap = makeAnswerMap( crMap, Answer.FormatType.HELPDESK, chaiConfiguration );
        final Map<Challenge, HelpdeskAnswer> returnMap = new LinkedHashMap<>();
        for ( final Map.Entry<Challenge, Answer> entry : tempMap.entrySet() )
        {
            returnMap.put( entry.getKey(), ( HelpdeskAnswer ) entry.getValue() );
        }
        return returnMap;
    }


    private static Map<Challenge, Answer> makeAnswerMap(
            final Map<Challenge, String> crMap,
            final ChaiConfiguration chaiConfiguration
    )
    {
        final Answer.FormatType formatType = Answer.FormatType.valueOf( chaiConfiguration.getSetting( ChaiSetting.CR_DEFAULT_FORMAT_TYPE ) );
        return makeAnswerMap( crMap, formatType, chaiConfiguration );
    }

    private static Map<Challenge, Answer> makeAnswerMap(
            final Map<Challenge, String> crMap,
            final Answer.FormatType formatType,
            final ChaiConfiguration chaiConfiguration
    )
    {
        final Map<Challenge, Answer> answerMap = new LinkedHashMap<>();
        for ( final Map.Entry<Challenge, String> entry : crMap.entrySet() )
        {
            final Challenge challenge = entry.getKey();
            final String answerText = entry.getValue();

            final int iterations;
            {
                final int configuredIterations = Integer.parseInt( chaiConfiguration.getSetting( ChaiSetting.CR_CHAI_ITERATIONS ) );
                iterations = configuredIterations > 0
                        ? configuredIterations
                        : formatType.getDefaultIterations();
            }

            final int saltCharCount;
            {
                final int configuredIterations = Integer.parseInt( chaiConfiguration.getSetting( ChaiSetting.CR_CHAI_SALT_CHAR_COUNT ) );
                saltCharCount = configuredIterations > 0
                        ? configuredIterations
                        : formatType.getSaltLength();
            }


            final AnswerConfiguration answerConfiguration = AnswerConfiguration.builder()
                    .caseInsensitive( chaiConfiguration.getBooleanSetting( ChaiSetting.CR_CASE_INSENSITIVE ) )
                    .iterations( iterations )
                    .saltCharCount( saltCharCount )
                    .formatType( formatType )
                    .challengeText( challenge.getChallengeText() )
                    .build();


            final Answer answer = AnswerFactory.newAnswer( answerConfiguration, answerText );
            answerMap.put( challenge, answer );
        }
        return answerMap;
    }

    private static void validateAnswers( final Map<Challenge, String> crMap, final ChaiConfiguration chaiConfiguration )
            throws ChaiValidationException
    {
        final boolean allowDuplicates = chaiConfiguration.getBooleanSetting( ChaiSetting.CR_ALLOW_DUPLICATE_RESPONSES );
        for ( final Map.Entry<Challenge, String> entry : crMap.entrySet() )
        {
            final Challenge loopChallenge = entry.getKey();
            final String answerText = entry.getValue();

            if ( loopChallenge.getChallengeText() == null || loopChallenge.getChallengeText().length() < 1 )
            {
                throw new ChaiValidationException( "challenge text missing for challenge", ChaiError.CR_MISSING_REQUIRED_CHALLENGE_TEXT );
            }

            if ( answerText == null || answerText.length() < 1 )
            {
                final String errorString = "response text missing for challenge '" + loopChallenge.getChallengeText() + "'";
                throw new ChaiValidationException( errorString, ChaiError.CR_MISSING_REQUIRED_RESPONSE_TEXT, loopChallenge.getChallengeText() );
            }

            if ( answerText.length() < loopChallenge.getMinLength() )
            {
                final String errorString = "response text is too short for challenge '" + loopChallenge.getChallengeText() + "'";
                throw new ChaiValidationException( errorString, ChaiError.CR_RESPONSE_TOO_SHORT, loopChallenge.getChallengeText() );
            }

            if ( answerText.length() > loopChallenge.getMaxLength() )
            {
                final String errorString = "response text is too long for challenge '" + loopChallenge.getChallengeText() + "'";
                throw new ChaiValidationException( errorString, ChaiError.CR_RESPONSE_TOO_LONG, loopChallenge.getChallengeText() );
            }

            if ( loopChallenge.getMaxQuestionCharsInAnswer() > 0 )
            {
                final int maxChallengeLengthInResponse = loopChallenge.getMaxQuestionCharsInAnswer();
                if ( loopChallenge.getChallengeText() != null )
                {
                    final String[] challengeWords = loopChallenge.getChallengeText().toLowerCase().split( "\\s" );
                    final String responseTextLower = answerText.toLowerCase();
                    for ( final String challengeWord : challengeWords )
                    {
                        if ( challengeWord.length() > maxChallengeLengthInResponse )
                        {
                            for ( int i = 0; i <= challengeWord.length() - ( maxChallengeLengthInResponse + 1 ); i++ )
                            {
                                final String wordPart = challengeWord.substring( i, i + ( maxChallengeLengthInResponse + 1 ) );
                                if ( responseTextLower.contains( wordPart ) )
                                {
                                    final String errorString = "response text contains too many challenge characters for challenge '" + loopChallenge.getChallengeText() + "'";
                                    throw new ChaiValidationException( errorString, ChaiError.CR_TOO_MANY_QUESTION_CHARS, loopChallenge.getChallengeText() );
                                }
                            }
                        }
                    }
                }

            }
        }

        if ( !allowDuplicates )
        {
            final Set<String> seenResponses = new HashSet<>();
            for ( final Map.Entry<Challenge, String> entry : crMap.entrySet() )

            {
                final Challenge loopChallenge  = entry.getKey();
                final String responseText = entry.getValue();
                if ( responseText != null && responseText.length() > 1 )
                {
                    final String lowercaseResponseText = responseText.toLowerCase();
                    if ( seenResponses.contains( lowercaseResponseText ) )
                    {
                        throw new ChaiValidationException( "multiple responses have the same value", ChaiError.CR_DUPLICATE_RESPONSES, loopChallenge.getChallengeText() );
                    }
                    seenResponses.add( lowercaseResponseText );
                }
            }
        }
    }

    private ChaiCrFactory()
    {
    }

    /**
     * Read the user's configured ResponseSet from the directory.
     * A caller would typically use the returned {@code ResponseSet} for testing responses by calling
     * {@link ResponseSet#test(Map)}.
     *
     * @param user ChaiUser to read responses for
     * @return A valid ResponseSet if found, otherwise null.
     * @throws ChaiUnavailableException If the directory server(s) are unavailable
     * @throws ChaiOperationException   If an error is encountered during the operation
     * @throws ChaiValidationException when there is a logical problem with the response set data, such as more randoms required then exist
     */
    public static ChaiResponseSet readChaiResponseSet( final ChaiUser user )
            throws ChaiUnavailableException, ChaiValidationException, ChaiOperationException
    {
        return ChaiResponseSet.readUserResponseSet( user );
    }

    public static ResponseSet parseChaiResponseSetXML( final String inputXmlString )
            throws ChaiValidationException, ChaiOperationException
    {
        return ChaiResponseSet.ChaiResponseXmlParser.parseChaiResponseSetXML( inputXmlString );
    }
}

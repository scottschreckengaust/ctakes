/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ctakes.dictionary.lookup2.consumer;

import org.apache.ctakes.dictionary.lookup2.textspan.MultiTextSpan;
import org.apache.ctakes.dictionary.lookup2.textspan.TextSpan;
import org.apache.ctakes.dictionary.lookup2.term.RareWordTerm;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Refine a collection of dictionary terms to only contain the most specific variations:
 * "colon cancer" instead of "cancer", performed by span inclusion / complete containment, not overlap
 *
 * Author: SPF
 * Affiliation: CHIP-NLP
 * Date: 1/9/14
 */
final public class PrecisionTermConsumer extends AbstractTermConsumer {

   private final AbstractTermConsumer _idHitConsumer;

   public PrecisionTermConsumer( final UimaContext uimaContext, final Properties properties ) {
      super( uimaContext, properties );
      _idHitConsumer = new DefaultTermConsumer( uimaContext, properties );
   }

   /**
    * Only uses the largest spans for the type
    * {@inheritDoc}
    */
   @Override
   protected void consumeTypeIdHits( final JCas jcas, final String codingScheme, final int typeId,
                                     final Map<TextSpan, Collection<RareWordTerm>> lookupHitMap )
         throws AnalysisEngineProcessException {
      final Map<TextSpan, Collection<RareWordTerm>> preciseHitMap = createPreciseHitMap( lookupHitMap );
      _idHitConsumer.consumeTypeIdHits( jcas, codingScheme, typeId, preciseHitMap );
   }

   /**
    * Refine a collection of dictionary terms to only contain the most specific variations:
    * "colon cancer" instead of "cancer", performed by span inclusion /complete containment, not overlap
    * @param lookupHitMap terms in the dictionary
    * @return terms with the longest spans
    */
   static private Map<TextSpan, Collection<RareWordTerm>> createPreciseHitMap(
         final Map<TextSpan, Collection<RareWordTerm>> lookupHitMap ) {
      final Collection<TextSpan> discardSpans = new HashSet<TextSpan>();
      final List<TextSpan> textSpans = new ArrayList<TextSpan>( lookupHitMap.keySet() );
      final int count = textSpans.size();
      for ( int i=0; i<count; i++ ) {
         final TextSpan spanKeyI = textSpans.get( i );
         for ( int j=i+1; j<count; j++ ) {
            final TextSpan spanKeyJ = textSpans.get( j );
            if ( (spanKeyJ.getStart() <= spanKeyI.getStart() && spanKeyJ.getEnd() > spanKeyI.getEnd())
                  || (spanKeyJ.getStart() < spanKeyI.getStart() && spanKeyJ.getEnd() >= spanKeyI.getEnd()) ) {
               // J contains I, discard less precise concepts for span I and move on to next span I
               if ( spanKeyJ instanceof MultiTextSpan ) {
                  boolean spanIok = false;
                  for ( TextSpan missingSpanKey : ((MultiTextSpan)spanKeyJ).getMissingSpans() ) {
                     if ( (missingSpanKey.getStart() >= spanKeyI.getStart() && missingSpanKey.getStart() < spanKeyI.getEnd())
                           || (missingSpanKey.getEnd() > spanKeyI.getStart() && missingSpanKey.getEnd() <= spanKeyI.getEnd()) ) {
                        // I overlaps a missing span, so it is actually ok
                        spanIok = true;
                        break;
                     }
                  }
                  if ( !spanIok ) {
                     discardSpans.add( spanKeyI );
                     break;
                  }
               } else {
                  discardSpans.add( spanKeyI );
                  break;
               }
            }
            if ( ( (spanKeyI.getStart() <= spanKeyJ.getStart() && spanKeyI.getEnd() > spanKeyJ.getEnd() )
                  || (spanKeyI.getStart() < spanKeyJ.getStart() && spanKeyI.getEnd() >= spanKeyJ.getEnd()) ) ) {
               // I contains J, discard less precise concepts for span J and move on to next span J
               if ( spanKeyI instanceof MultiTextSpan ) {
                  boolean spanJok = false;
                  for ( TextSpan missingSpanKey : ((MultiTextSpan)spanKeyI).getMissingSpans() ) {
                     if ( (missingSpanKey.getStart() >= spanKeyJ.getStart() && missingSpanKey.getStart() < spanKeyJ.getEnd())
                           || (missingSpanKey.getEnd() > spanKeyJ.getStart() && missingSpanKey.getEnd() <= spanKeyJ.getEnd()) ) {
                        // J overlaps a missing span, so it is actually ok
                        spanJok = true;
                        break;
                     }
                  }
                  if ( !spanJok ) {
                     discardSpans.add( spanKeyJ );
                  }
               } else {
                  discardSpans.add( spanKeyJ );
               }
            }
         }
      }
      final Map<TextSpan, Collection<RareWordTerm>> preciseHitMap
            = new HashMap<TextSpan, Collection<RareWordTerm>>( lookupHitMap.size() - discardSpans.size() );
      for ( Map.Entry<TextSpan,Collection<RareWordTerm>> entry : lookupHitMap.entrySet() ) {
         if ( !discardSpans.contains( entry.getKey() ) ) {
            preciseHitMap.put( entry.getKey(), entry.getValue() );
         }
      }
      return preciseHitMap;
   }

}
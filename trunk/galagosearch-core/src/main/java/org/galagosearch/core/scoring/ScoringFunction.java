/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.galagosearch.core.scoring;

/**
 * Interface of a class that transforms a count into a score.
 * Implementations of this are primarily used by a ScoringFunctionIterator
 *
 * @author marc
 */
public interface ScoringFunction {
    public double score(int count, int length);
}
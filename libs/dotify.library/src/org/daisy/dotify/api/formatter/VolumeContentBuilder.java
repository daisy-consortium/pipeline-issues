package org.daisy.dotify.api.formatter;

/**
 * Provides methods needed to build volume anchored content,
 * such as cover pages and TOC.
 *
 * @author Joel Håkansson
 */
public interface VolumeContentBuilder extends FormatterCore {

    /**
     * Creates a new sequence at the current position.
     *
     * @param props the properties of the sequence
     */
    public void newSequence(SequenceProperties props);

    /**
     * Creates a new toc sequence at the current position.
     *
     * @param props the properties of the toc sequence
     */
    public void newTocSequence(TocProperties props);

    /**
     * <p>Creates a new on toc start block. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @param condition a condition, or null
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnTocStart(Condition condition);

    /**
     * <p>Creates a new on toc start block that always apply. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnTocStart();

    /**
     * <p>Creates a new on volume start block. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @param condition a condition, or null
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnVolumeStart(Condition condition);

    /**
     * <p>Creates a new on volume start block that always apply. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnVolumeStart();

    /**
     * <p>Creates a new on volume end block. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @param condition a condition, or null
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnVolumeEnd(Condition condition);

    /**
     * <p>Creates a new on volume end block that always apply. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnVolumeEnd();

    /**
     * <p>Creates a new on toc end block. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @param condition a condition, or null
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnTocEnd(Condition condition);

    /**
     * <p>Creates a new on toc end block that always apply. In a scenario where conditions
     * overlap, the first one added takes precedence.</p>
     * <p>Calling this method is only valid within a TOC sequence.</p>
     *
     * @throws IllegalStateException if not in a TOC sequence
     */
    public void newOnTocEnd();

    /**
     * Creates a new dynamic sequence.
     *
     * @param props the sequence properties
     * @return returns a new dynamic sequence
     */
    //TODO: add special dynamic sequence properties
    public DynamicSequenceBuilder newDynamicSequence(SequenceProperties props);

}

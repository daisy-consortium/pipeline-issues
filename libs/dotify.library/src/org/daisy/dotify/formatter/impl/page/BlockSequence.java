package org.daisy.dotify.formatter.impl.page;

import org.daisy.dotify.api.formatter.FormatterSequence;
import org.daisy.dotify.api.formatter.SequenceProperties;
import org.daisy.dotify.formatter.impl.core.BlockContext;
import org.daisy.dotify.formatter.impl.core.FormatterContext;
import org.daisy.dotify.formatter.impl.core.FormatterCoreImpl;
import org.daisy.dotify.formatter.impl.core.LayoutMaster;

import java.util.List;

/**
 * Provides an interface for a sequence of block contents.
 *
 * <p>{@link #selectScenario(LayoutMaster, BlockContext, boolean) selectScenario} is used to access
 * the blocks, as a list of {@link RowGroupSequence}s (one RowGroupSequence for each block).
 *
 * @author Joel Håkansson
 */
public class BlockSequence extends FormatterCoreImpl implements FormatterSequence {
    private static final long serialVersionUID = -6105005856680272131L;
    private final LayoutMaster master;
    private final SequenceProperties props;
    private List<RowGroupSequence> cache;

    public BlockSequence(FormatterContext fc, SequenceProperties props, LayoutMaster master) {
        super(fc);
        this.props = props;
        this.master = master;
    }

    /**
     * Gets the layout master for this sequence.
     *
     * @return returns the layout master for this sequence
     */
    public LayoutMaster getLayoutMaster() {
        return master;
    }

    /**
     * Get the initial page number, i.e. the number that the first page in the sequence should have.
     *
     * @return returns the initial page number, or null if no initial page number has been specified
     */
    public Integer getInitialPageNumber() {
        return props.getInitialPageNumber();
    }

    public SequenceProperties getSequenceProperties() {
        return props;
    }

    /**
     * Removes additional scenarios from the block list.
     *
     * @param master   the layout master
     * @param bc       the block context
     * @param useCache if true, only select scenario once. This may reduce the accuracy somewhat,
     *                 but it's probably not worth the processing.
     * @return returns the filtered block sequence
     */
    List<RowGroupSequence> selectScenario(LayoutMaster master, BlockContext bc, boolean useCache) {
        if (cache == null || !useCache) {
            cache = ScenarioProcessor.process(master, this, bc);
        }
        return cache;
    }

}

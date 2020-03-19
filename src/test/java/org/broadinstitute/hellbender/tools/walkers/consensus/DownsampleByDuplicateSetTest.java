package org.broadinstitute.hellbender.tools.walkers.consensus;

import htsjdk.samtools.SamReader;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableInt;
import org.broadinstitute.hellbender.CommandLineProgramTest;
import org.broadinstitute.hellbender.engine.ReadsDataSource;
import org.broadinstitute.hellbender.exceptions.UserException;
import org.broadinstitute.hellbender.testutils.ArgumentsBuilder;
import org.broadinstitute.hellbender.utils.iterators.SamReaderQueryingIterator;
import org.broadinstitute.hellbender.utils.read.GATKRead;
import org.broadinstitute.hellbender.utils.runtime.ProcessController;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import static org.testng.Assert.*;

public class DownsampleByDuplicateSetTest extends CommandLineProgramTest {
    public static final String NA12878_GROUPED = publicTestDir + "org/broadinstitute/hellbender/tools/downsampleByDuplicateSet/NA12878.grouped.bam";

    @Test
    public void testMatesAreTogether(){
        final File out = createTempFile("downsampled", "bam");
        final ArgumentsBuilder args = new ArgumentsBuilder()
                .add("I", NA12878_GROUPED)
                .add("O", out.getAbsolutePath())
                .add("DS", "1.0");
        runCommandLine(args, DownsampleByDuplicateSet.class.getSimpleName());

        final ReadsDataSource readsDataSource = new ReadsDataSource(Paths.get(out.getAbsolutePath()));
        final Iterator<GATKRead> iterator = readsDataSource.iterator();
        while (iterator.hasNext()){
            // Make sure that the read and its mate are next to each other in the file
            final GATKRead read1 = iterator.next();
            final GATKRead read2 = iterator.next();
            Assert.assertEquals(read1.getName(), read2.getName());
        }
    }

    /** When down-sampling rate is 1.0, the input file is returned unchanged **/
    @Test
    public void testNoDownsampling(){
        final File out = createTempFile("downasampled", "bam");
        final double downsampleRate = 1.0;
        final ArgumentsBuilder args = new ArgumentsBuilder()
                .add("I", NA12878_GROUPED)
                .add("DS", Double.toString(downsampleRate))
                .add("O", out.getAbsolutePath());
        runCommandLine(args, DownsampleByDuplicateSet.class.getSimpleName());

        final ReadsDataSource originalBam = new ReadsDataSource(Paths.get(NA12878_GROUPED));
        final Map<String, MutableInt> originalMoleculeCounts = molecularIDsAndCounts(originalBam);

        final ReadsDataSource downsampledBam = new ReadsDataSource(Paths.get(out.getAbsolutePath()));
        final Map<String, MutableInt> downsampledMoleculeCounts = molecularIDsAndCounts(downsampledBam);

        for (Map.Entry<String, MutableInt> originalIDAndCount : originalMoleculeCounts.entrySet()){
            final String originalID = originalIDAndCount.getKey();
            final int originalCount = originalIDAndCount.getValue().intValue();
            Assert.assertTrue(originalCount == downsampledMoleculeCounts.get(originalID).intValue());
        }
    }

    /**
     * Test that the downsampling rate corresponds to the reduction in the number of duplicates in the output
     * file up to sampling noise.
     */
    @Test
    public void testDownsampleFraction(){
        final File out = createTempFile("downasampled", "bam");
        for (double downsampleRate : Arrays.asList(0.1, 0.3, 0.5)){
            final ArgumentsBuilder args = new ArgumentsBuilder()
                    .add("I", NA12878_GROUPED)
                    .add("DS", Double.toString(downsampleRate))
                    .add("O", out.getAbsolutePath());
            runCommandLine(args, DownsampleByDuplicateSet.class.getSimpleName());

            final ReadsDataSource originalBam = new ReadsDataSource(Paths.get(NA12878_GROUPED));
            final int originalMoleculeCount = countDuplicateSets(originalBam);

            final ReadsDataSource downsampledBam = new ReadsDataSource(Paths.get(out.getAbsolutePath()));
            final int downsampledMoleculeCount = countDuplicateSets(downsampledBam);

            final double noise = 2.0;
            final double deviationFromExpected = Math.abs(downsampleRate * originalMoleculeCount - downsampledMoleculeCount);
            Assert.assertTrue(deviationFromExpected < noise);
        }
    }

    private int countDuplicateSets(final ReadsDataSource readsDataSource){
        int count = 0;
        String currentMolecularId = ""; // Note we are duplex aware: 12/A different from 12/B
        final Iterator<GATKRead> iterator = readsDataSource.iterator();
        while (iterator.hasNext()){
            final GATKRead read = iterator.next();
            final String molecularID = read.getAttributeAsString("MI");
            if (!molecularID.equals(currentMolecularId)){
                count++;
                currentMolecularId = molecularID;
            }
        }

        return count;
    }

    private Map<String, MutableInt> molecularIDsAndCounts(final ReadsDataSource readsDataSource){
        final Map<String, MutableInt> map = new TreeMap<>();
        final Iterator<GATKRead> iterator = readsDataSource.iterator();
        while (iterator.hasNext()){
            final GATKRead read = iterator.next();
            final String molecularID = read.getAttributeAsString("MI"); // Note we are duplex aware: 12/A different from 12/B
            if (map.containsKey(molecularID)){
                map.get(molecularID).increment();
            } else {
                map.put(molecularID, new MutableInt(0));
            }
        }
        return map;
    }
}
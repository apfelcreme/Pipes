package io.github.apfelcreme.Pipes.Manager;

import com.destroystokyo.paper.MaterialTags;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import io.github.apfelcreme.Pipes.Exception.ChunkNotLoadedException;
import io.github.apfelcreme.Pipes.Exception.PipeTooLongException;
import io.github.apfelcreme.Pipes.Exception.TooManyOutputsException;
import io.github.apfelcreme.Pipes.Pipe.AbstractPipePart;
import io.github.apfelcreme.Pipes.Pipe.ChunkLoader;
import io.github.apfelcreme.Pipes.Pipe.Pipe;
import io.github.apfelcreme.Pipes.Pipe.PipeInput;
import io.github.apfelcreme.Pipes.Pipe.PipeOutput;
import io.github.apfelcreme.Pipes.Pipe.SimpleLocation;
import io.github.apfelcreme.Pipes.PipesConfig;
import io.github.apfelcreme.Pipes.PipesItem;
import io.github.apfelcreme.Pipes.PipesUtil;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

/*
 * Copyright (C) 2016 Lord36 aka Apfelcreme
 * <p>
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * @author Lord36 aka Apfelcreme
 */
public class PipeManager {

    /**
     * the DetectionManager instance
     */
    private static PipeManager instance = null;

    /**
     * a cache to stop endless pipe checks
     */
    private final Cache<SimpleLocation, Pipe> pipeCache;

    /**
     * a cache to stop endless pipe checks, this is for parts that can be attached to only one pipe (glass pipe blocks)
     */
    private final Map<SimpleLocation, Pipe> singleCache;

    /**
     * a cache to stop endless pipe checks, this is for parts that can be attached to multiple pipes (outputs and chunk loader)
     */
    private final Map<SimpleLocation, Set<Pipe>> multiCache;

    /**
     * A cache for pipe parts
     */
    private final Map<SimpleLocation, AbstractPipePart> pipePartCache;

    /**
     * constructor
     */
    private PipeManager() {
        pipeCache = CacheBuilder.newBuilder()
                .maximumSize(PipesConfig.getPipeCacheSize())
                .expireAfterWrite(PipesConfig.getPipeCacheDuration(), TimeUnit.SECONDS)
                .removalListener(new PipeRemovalListener())
                .build();
        singleCache = new HashMap<>();
        multiCache = new HashMap<>();
        pipePartCache = new HashMap<>();
    }

    /**
     * returns the pipe cache
     *
     * @return the pipe cache
     */
    public Cache<SimpleLocation, Pipe> getPipeCache() {
        return pipeCache;
    }

    /**
     * returns the cache for blocks that can only belong to a single pipe (and aren't inputs)
     *
     * @return the single cache
     */
    public Map<SimpleLocation, Pipe> getSingleCache() {
        return singleCache;
    }

    /**
     * returns the cache for blocks that can belong to multiple pipes (outputs and chunk loaders)
     *
     * @return the multi cache
     */
    public Map<SimpleLocation, Set<Pipe>> getMultiCache() {
        return multiCache;
    }

    /**
     * returns the cache for pipe parts
     *
     * @return the pipe part cache
     */
    public Map<SimpleLocation, AbstractPipePart> getPipePartCache() {
        return pipePartCache;
    }

    /**
     * returns the PipeManager instance
     *
     * @return the PipeManager instance
     */
    public static PipeManager getInstance() {
        if (instance == null) {
            instance = new PipeManager();
        }
        return instance;
    }

    /**
     * Get the pipe by an input at a location. This will only lookup in the input cache and no other one.
     * If none is found it will try to calculate the pipe that starts at that position
     *
     * @param location the location the input is at
     * @return a Pipe or <code>null</code>
     * @throws ChunkNotLoadedException When the pipe reaches into a chunk that is not loaded
     * @throws PipeTooLongException When the pipe is too long
     * @throws TooManyOutputsException when the pipe has too many outputs
     */
    public Pipe getPipeByInput(SimpleLocation location) throws ChunkNotLoadedException, TooManyOutputsException, PipeTooLongException {
        Pipe pipe = pipeCache.getIfPresent(location);
        if (pipe == null) {
            Block block = location.getBlock();

            if (PipesUtil.getPipesItem(block) != PipesItem.PIPE_INPUT) {
                return null;
            }

            pipe = isPipe(block);
            if (pipe != null) {
                addPipe(pipe);
            }
        } else {
            pipe.checkLoaded(location);
        }
        return pipe;
    }

    /**
     * Get the pipe that is at that location, returns an empty set instead of throwing an exception
     *
     * @param location The location
     * @return the pipes; an empty set if none were found or an error occurred
     */
    public Set<Pipe> getPipesSafe(SimpleLocation location) {
        return getPipesSafe(location, false);
    }

    /**
     * Get the pipe that is at that location, returns an empty set instead of throwing an exception
     *
     * @param location The location
     * @param cacheOnly Only look in the cache, don't search for new ones
     * @return the pipes; an empty set if none were found or an error occurred
     */
    public Set<Pipe> getPipesSafe(SimpleLocation location, boolean cacheOnly) {
        if (cacheOnly) {
            Pipe pipe = pipeCache.getIfPresent(location);
            if (pipe == null) {
                pipe = singleCache.get(location);
            }
            if (pipe != null) {
                return Collections.singleton(pipe);
            }
            return multiCache.getOrDefault(location, Collections.emptySet());
        }
        try {
            return getPipes(location.getBlock(), false);
        } catch (ChunkNotLoadedException | PipeTooLongException | TooManyOutputsException e) {
            return Collections.emptySet();
        }
    }

    /**
     * Get the pipe, returns an empty set instead of throwing an exception
     *
     * @param block the block to get the pipe for
     * @return the pipes; an empty set if none were found or an error occurred
     */
    public Set<Pipe> getPipesSafe(Block block) {
        try {
            return getPipes(block);
        } catch (ChunkNotLoadedException | PipeTooLongException | TooManyOutputsException e) {
            return Collections.emptySet();
        }
    }

    /**
     * Get the pipe, returns an empty set instead of throwing an exception
     *
     * @param block the block to get the pipe for
     * @param cacheOnly Only look in the cache, don't search for new ones
     * @return the pipes; an empty set if none were found or an error occurred
     */
    public Set<Pipe> getPipesSafe(Block block, boolean cacheOnly) {
        try {
            return getPipes(block, cacheOnly);
        } catch (ChunkNotLoadedException | PipeTooLongException | TooManyOutputsException e) {
            return Collections.emptySet();
        }
    }

    /**
     * Get the pipe for a block
     *
     * @param block The block
     * @return the pipes; an empty set if none were found
     * @throws ChunkNotLoadedException When the pipe reaches into a chunk that is not loaded
     * @throws PipeTooLongException When the pipe is too long
     * @throws TooManyOutputsException when the pipe has too many outputs
     */
    public Set<Pipe> getPipes(Block block) throws ChunkNotLoadedException, PipeTooLongException, TooManyOutputsException {
        return getPipes(block, false);
    }

    /**
     * Get the pipe for a block
     *
     * @param block The block
     * @param cacheOnly Only look in the cache, don't search for new ones
     * @return the pipes; an empty set if none were found
     * @throws ChunkNotLoadedException When the pipe reaches into a chunk that is not loaded
     * @throws PipeTooLongException When the pipe is too long
     * @throws TooManyOutputsException when the pipe has too many outputs
     */
    public Set<Pipe> getPipes(Block block, boolean cacheOnly) throws ChunkNotLoadedException, PipeTooLongException, TooManyOutputsException {
        if (block == null) {
            return Collections.emptySet();
        }
        Set<Pipe> pipes = getPipesSafe(new SimpleLocation(block.getLocation()), true);
        if (pipes.isEmpty() && !cacheOnly) {
            Pipe pipe = isPipe(block);
            if (pipe != null) {
                addPipe(pipe);
                return Collections.singleton(pipe);
            }
        }
        return pipes;
    }

    public void removePipe(Pipe pipe) {
        if (pipe == null) {
            return;
        }

        for (Iterator<PipeInput> i = pipe.getInputs().values().iterator(); i.hasNext();) {
            PipeInput input = i.next();
            i.remove();
            pipeCache.invalidate(input.getLocation());
        }
    }

    /**
     * Add all the pipes locations to the cache
     * @param pipe The pipe
     */
    private void addPipe(Pipe pipe) {
        if (pipe == null) {
            return;
        }
        for (PipeInput input : pipe.getInputs().values()) {
            pipeCache.put(input.getLocation(), pipe);
            pipePartCache.put(input.getLocation(), input);
            if (!input.getHolder().getInventory().isEmpty()) {
                ItemMoveScheduler.getInstance().add(input.getLocation());
            }
        }
        for (SimpleLocation location : pipe.getPipeBlocks()) {
            singleCache.put(location, pipe);
        }
        for (PipeOutput output : pipe.getOutputs().values()) {
            addToMultiCache(output.getLocation(), pipe);
            pipePartCache.put(output.getLocation(), output);
        }
        for (ChunkLoader chunkLoader : pipe.getChunkLoaders().values()) {
            addToMultiCache(chunkLoader.getLocation(), pipe);
            pipePartCache.put(chunkLoader.getLocation(), chunkLoader);
        }
    }

    /**
     * Add a part to a pipe while checking settings and caching the location
     *
     * @param pipe the pipe to add to
     * @param pipePart the part to add
     * @throws TooManyOutputsException when the pipe has too many outputs
     */
    public void addPart(Pipe pipe, AbstractPipePart pipePart) throws TooManyOutputsException {
        if (pipePart instanceof PipeInput) {
            pipe.getInputs().put(pipePart.getLocation(), (PipeInput) pipePart);
            for (PipeInput input : pipe.getInputs().values()) {
                pipeCache.put(input.getLocation(), pipe);
            }
        } else if (pipePart instanceof PipeOutput) {
            if (PipesConfig.getMaxPipeOutputs() > 0 && pipe.getOutputs().size() + 1 >= PipesConfig.getMaxPipeOutputs()) {
                removePipe(pipe);
                throw new TooManyOutputsException(pipePart.getLocation());
            }
            pipe.getOutputs().put(pipePart.getLocation(), (PipeOutput) pipePart);
            addToMultiCache(pipePart.getLocation(), pipe);
        } else if (pipePart instanceof ChunkLoader) {
            pipe.getChunkLoaders().put(pipePart.getLocation(), (ChunkLoader) pipePart);
            addToMultiCache(pipePart.getLocation(), pipe);
        }
        pipePartCache.put(pipePart.getLocation(), pipePart);
    }

    /**
     * Remove a part from a pipe
     *
     * @param pipe the pipe to remove from
     * @param pipePart the part to remove
     */
    public void removePart(Pipe pipe, AbstractPipePart pipePart) {
        if (pipePart instanceof PipeInput) {
            pipe.getInputs().remove(pipePart.getLocation());
            pipeCache.invalidate(pipePart.getLocation());
        } else if (pipePart instanceof PipeOutput) {
            pipe.getOutputs().remove(pipePart.getLocation());
            if (pipe.getOutputs().isEmpty()) {
                removePipe(pipe);
            } else {
                removeFromMultiCache(pipePart.getLocation(), pipe);
            }
        } else if (pipePart instanceof ChunkLoader) {
            pipe.getChunkLoaders().remove(pipePart.getLocation());
            removeFromMultiCache(pipePart.getLocation(), pipe);
        }
        pipePartCache.remove(pipePart.getLocation(), pipePart);
    }

    private void addToMultiCache(SimpleLocation location, Pipe pipe) {
        multiCache.putIfAbsent(location, Collections.newSetFromMap(new WeakHashMap<>()));
        multiCache.get(location).add(pipe);
    }

    private void removeFromMultiCache(SimpleLocation location, Pipe pipe) {
        Collection<Pipe> pipes = multiCache.get(location);
        if (pipes != null) {
            if (pipes.size() == 1) {
                multiCache.remove(location);
            } else {
                pipes.remove(pipe);
            }
        }
    }

    /**
     * Add a block to a pipe while checking settings and caching the location
     *
     * @param pipe the pipe to add to
     * @param block the block to add
     * @throws PipeTooLongException When the pipe is too long
     */
    public void addBlock(Pipe pipe, Block block) throws PipeTooLongException {
        SimpleLocation location = new SimpleLocation(block.getLocation());
        if (PipesConfig.getMaxPipeLength() > 0 && pipe.getPipeBlocks().size() >= PipesConfig.getMaxPipeLength()) {
            removePipe(pipe);
            throw new PipeTooLongException(location);
        }
        pipe.getPipeBlocks().add(location);
        singleCache.put(location, pipe);
    }

    /**
     * Merge multiple pipes into one
     * @param pipes The pipes to merge
     * @return the merged Pipe or <code>null</code> if they couldn't be merged
     * @throws PipeTooLongException When the pipe is too long
     * @throws TooManyOutputsException when the pipe has too many outputs
     */
    public Pipe mergePipes(Set<Pipe> pipes) throws TooManyOutputsException, PipeTooLongException {
        Material type = null;
        for (Pipe pipe : pipes) {
            if (type == null) {
                type = pipe.getType();
            }
            if (pipe.getType() != type) {
                return null;
            }
        }

        LinkedHashMap<SimpleLocation, PipeInput> inputs = new LinkedHashMap<>();
        LinkedHashMap<SimpleLocation, PipeOutput> outputs = new LinkedHashMap<>();
        LinkedHashMap<SimpleLocation, ChunkLoader> chunkLoaders = new LinkedHashMap<>();
        LinkedHashSet<SimpleLocation> blocks = new LinkedHashSet<>();

        pipes.forEach(pipe -> {
            removePipe(pipe);
            inputs.putAll(pipe.getInputs());
            outputs.putAll(pipe.getOutputs());
            chunkLoaders.putAll(pipe.getChunkLoaders());
            blocks.addAll(pipe.getPipeBlocks());
        });

        if (PipesConfig.getMaxPipeLength() > 0 &&blocks.size() >= PipesConfig.getMaxPipeLength()) {
            throw new PipeTooLongException(blocks.iterator().next());
        }

        if (PipesConfig.getMaxPipeOutputs() > 0 && outputs.size() + 1 >= PipesConfig.getMaxPipeOutputs()) {
            throw new TooManyOutputsException(outputs.keySet().iterator().next());
        }

        Pipe pipe = new Pipe(inputs, outputs, chunkLoaders, blocks, type);

        addPipe(pipe);
        return pipe;
    }

    /**
     * checks if the block is part of a pipe.
     *
     * @param startingPoint a block
     * @return a pipe, if there is one
     * @throws ChunkNotLoadedException When the pipe reaches into a chunk that is not loaded
     * @throws PipeTooLongException When the pipe is too long
     * @throws TooManyOutputsException when the pipe has too many outputs
     */
    public Pipe isPipe(Block startingPoint) throws ChunkNotLoadedException, TooManyOutputsException, PipeTooLongException {

        Queue<SimpleLocation> queue = new LinkedList<>();
        Set<SimpleLocation> found = new LinkedHashSet<>();

        LinkedHashMap<SimpleLocation, PipeInput> inputs = new LinkedHashMap<>();
        LinkedHashMap<SimpleLocation, PipeOutput> outputs = new LinkedHashMap<>();
        LinkedHashMap<SimpleLocation, ChunkLoader> chunkLoaders = new LinkedHashMap<>();
        LinkedHashSet<SimpleLocation> pipeBlocks = new LinkedHashSet<>();

        Material type = null;

        World world = startingPoint.getWorld();

        queue.add(new SimpleLocation(
                startingPoint.getWorld().getName(),
                startingPoint.getX(),
                startingPoint.getY(),
                startingPoint.getZ()));

        while (!queue.isEmpty()) {
            SimpleLocation location = queue.remove();
            if (!found.contains(location)) {
                if (!world.isChunkLoaded(location.getX() >> 4, location.getZ() >> 4)
                        && (chunkLoaders.size() == 0)) {
                    throw new ChunkNotLoadedException(location);
                }
                Block block = world.getBlockAt(location.getX(), location.getY(), location.getZ());
                if (MaterialTags.STAINED_GLASS.isTagged(block)) {
                    if (type == null) {
                        type = block.getType();
                    }
                    if (block.getType() == type) {
                        if (PipesConfig.getMaxPipeLength() > 0 && pipeBlocks.size() >= PipesConfig.getMaxPipeLength()) {
                            throw new PipeTooLongException(location);
                        }
                        pipeBlocks.add(location);
                        found.add(location);
                        for (BlockFace face : PipesUtil.BLOCK_FACES) {
                            queue.add(location.getRelative(face));
                        }
                    }
                } else {
                    AbstractPipePart pipesPart = getPipePart(block);
                    if (pipesPart != null) {
                        switch (pipesPart.getType()) {
                            case PIPE_INPUT:
                                PipeInput pipeInput = (PipeInput) pipesPart;
                                Block relativeBlock = block.getRelative(pipeInput.getFacing());
                                if (type == null && MaterialTags.STAINED_GLASS.isTagged(relativeBlock)) {
                                    type = relativeBlock.getType();
                                }
                                if (relativeBlock.getType() == type) {
                                    inputs.put(pipeInput.getLocation(), pipeInput);
                                    found.add(location);
                                    queue.add(pipeInput.getTargetLocation());
                                }
                                break;
                            case PIPE_OUTPUT:
                                PipeOutput pipeOutput = (PipeOutput) pipesPart;
                                if (PipesConfig.getMaxPipeOutputs() > 0 && outputs.size() >= PipesConfig.getMaxPipeOutputs()) {
                                    throw new TooManyOutputsException(location);
                                }
                                outputs.put(pipeOutput.getLocation(), pipeOutput);
                                if (found.isEmpty()) {
                                    for (BlockFace face : PipesUtil.BLOCK_FACES) {
                                        if (face != pipeOutput.getFacing()) {
                                            Material relative = block.getRelative(face).getType();
                                            if (relative == type || (type == null & MaterialTags.STAINED_GLASS.isTagged(relative))) {
                                                queue.add(location.getRelative(face));
                                                break;
                                            }
                                        }
                                    }
                                }
                                found.add(location);
                                Block relativeToOutput = pipeOutput.getTargetLocation().getBlock();
                                if (relativeToOutput.getState(false) instanceof InventoryHolder) {
                                    found.add(new SimpleLocation(relativeToOutput.getLocation()));
                                } else if (relativeToOutput.getType() == Material.COMPOSTER) {
                                    found.add(new SimpleLocation(relativeToOutput.getLocation()));
                                }
                                break;
                            case CHUNK_LOADER:
                                chunkLoaders.put(pipesPart.getLocation(), (ChunkLoader) pipesPart);
                                found.add(location);
                                break;
                        }
                    }
                }
            }
        }

        // Remove outputs that point in our own inputs
        for (Iterator<PipeOutput> it = outputs.values().iterator(); it.hasNext();) {
            PipeOutput pipeOutput = it.next();
            SimpleLocation targetLocation = pipeOutput.getTargetLocation();
            if (inputs.containsKey(targetLocation)) {
                it.remove();
            }
        }

        if ((outputs.size() > 0) && (inputs.size() > 0) && pipeBlocks.size() > 0) {
            return new Pipe(inputs, outputs, chunkLoaders, pipeBlocks, type);
        }
        return null;
    }

    /**
     * Create a new pipe part
     * @param item  The PipesItem to create the part from
     * @param block The block to create the part at
     * @return The pipepart
     */
    public AbstractPipePart createPipePart(PipesItem item, Block block) {
        BlockState state = block.getState(false);
        if (state instanceof Container) {
            // Paper's non-snapshot BlockState's are broken in some cases
            if (((Container) state).getPersistentDataContainer() == null) {
                state = block.getState(true);
            }
            ((Container) state).getPersistentDataContainer().set(AbstractPipePart.TYPE_KEY, PersistentDataType.STRING, item.name());
            state.update();
        }
        AbstractPipePart part = PipesUtil.convertToPipePart(state, item);
        pipePartCache.put(new SimpleLocation(block.getLocation()), part);
        return part;
    }

    /**
     * Get the pipes part. Will try to lookup the part in the cache first, if not found it will create a new one.
     * @param block the block to get the part for
     * @return the pipespart or null if the block isn't one
     */
    public AbstractPipePart getPipePart(Block block) {
        PipesItem type = PipesUtil.getPipesItem(block);
        if (type == null) {
            return null;
        }
        return pipePartCache.getOrDefault(
                new SimpleLocation(block.getLocation()),
                PipesUtil.convertToPipePart(block.getState(false), type)
        );
    }

    /**
     * Get the pipes part. Will try to lookup the part in the cache first, if not found it will create a new one.
     * @param state the block's state to get the part for
     * @return the pipespart or null if the block isn't one
     */
    public AbstractPipePart getPipePart(BlockState state) {
        PipesItem type = PipesUtil.getPipesItem(state);
        if (type == null) {
            return null;
        }
        return pipePartCache.getOrDefault(
                new SimpleLocation(state.getLocation()),
                PipesUtil.convertToPipePart(state, type)
        );
    }

    /**
     * Get the pipes part. Will try to lookup the part in the cache first, if not found it will create a new one.
     * @param location the block to get the part for
     * @return the pipespart or
     */
    public AbstractPipePart getCachedPipePart(SimpleLocation location) {
        return pipePartCache.get(location);
    }

    private class PipeRemovalListener implements RemovalListener<SimpleLocation, Pipe> {
        @Override
        public void onRemoval(RemovalNotification<SimpleLocation, Pipe> notification) {
            Pipe pipe = notification.getValue();

            if (pipe == null) {
                return;
            }

            if (pipe.getInputs().isEmpty() || notification.getCause() != RemovalCause.EXPLICIT) {
                for (PipeInput input : pipe.getInputs().values()) {
                    pipeCache.invalidate(input.getLocation());
                    pipePartCache.remove(input.getLocation(), input);
                }
                for (SimpleLocation location : pipe.getPipeBlocks()) {
                    singleCache.remove(location);
                }
                for (PipeOutput output : pipe.getOutputs().values()) {
                    removeFromMultiCache(output.getLocation(), pipe);
                    if (multiCache.getOrDefault(output.getLocation(), Collections.emptySet()).isEmpty()) {
                        pipePartCache.remove(output.getLocation(), output);
                    }
                }
                for (ChunkLoader loader : pipe.getChunkLoaders().values()) {
                    removeFromMultiCache(loader.getLocation(), pipe);
                    if (multiCache.getOrDefault(loader.getLocation(), Collections.emptySet()).isEmpty()) {
                        pipePartCache.remove(loader.getLocation(), loader);
                    }
                }
            }
        }
    }
}

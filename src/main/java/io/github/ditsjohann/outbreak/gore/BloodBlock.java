package io.github.ditsjohann.outbreak.gore;

import io.github.ditsjohann.outbreak.block.OutbreakBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Blood on the ground: drops, a splatter or a pool (SIZE 0-2), fresh to dried (AGE 0-3), turned any
 * of four ways (FACING) so neighbours don't repeat. It dries as time passes and is gone after about
 * twenty minutes (faster in the rain); nothing collides with it and anything can be put in its place.
 */
public class BloodBlock extends Block {
	public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 2);
	public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 0.5, 16.0);
	/** Chance a random tick moves it on to the next age (fresh dries quickly, the stain lingers). */
	private static final float[] AGEING = {0.7F, 0.35F, 0.2F, 0.1F};

	public BloodBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(SIZE, 0).setValue(AGE, 0).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return true;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		return direction == Direction.DOWN && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		boolean rain = level.isRainingAt(pos.above());
		if (!rain && random.nextFloat() >= AGEING[age]) {
			return;
		}

		if (age >= 3) {
			level.removeBlock(pos, false);
		} else {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected boolean canBeReplaced(BlockState state, net.minecraft.world.item.context.BlockPlaceContext context) {
		return true;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(SIZE, AGE, FACING);
	}

	/** Whether this spot has blood on it already. */
	public static boolean isBlood(BlockState state) {
		return state.is(OutbreakBlocks.BLOOD);
	}
}


import com.sun.tools.classfile.Instruction;

class AbstractInstructionVisitor<R, P> implements Instruction.KindVisitor<R, P> {

    private final R defaultValue;

    AbstractInstructionVisitor(R defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public R visitNoOperands(Instruction i, P p) {
        return defaultValue;
    }

    @Override
    public R visitArrayType(Instruction i, Instruction.TypeKind tk, P p) {
        return defaultValue;
    }

    @Override
    public R visitBranch(Instruction i, int i1, P p) {
        return defaultValue;
    }

    @Override
    public R visitConstantPoolRef(Instruction i, int i1, P p) {
        return defaultValue;
    }

    @Override
    public R visitConstantPoolRefAndValue(Instruction i, int i1, int i2, P p) {
        return defaultValue;
    }

    @Override
    public R visitLocal(Instruction i, int i1, P p) {
        return defaultValue;
    }

    @Override
    public R visitLocalAndValue(Instruction i, int i1, int i2, P p) {
        return defaultValue;
    }

    @Override
    public R visitLookupSwitch(Instruction i, int i1, int i2, int[] ints, int[] ints1, P p) {
        return defaultValue;
    }

    @Override
    public R visitTableSwitch(Instruction i, int i1, int i2, int i3, int[] ints, P p) {
        return defaultValue;
    }

    @Override
    public R visitValue(Instruction i, int i1, P p) {
        return defaultValue;
    }

    @Override
    public R visitUnknown(Instruction i, P p) {
        return defaultValue;
    }
}

#!/bin/bash

UI_INSTS=(sw lw add addi sub and andi or ori xor xori sll srl sra slli srli srai slt sltu slti sltiu beq bne blt bge bltu bgeu jal jalr lui auipc)
MI_INSTS=(csr scall)

WORK_DIR=/src/chisel-template
RESULT_DIR=$WORK_DIR/results
mkdir -p $RESULT_DIR
cd $WORK_DIR

function loop_test() {
  INSTS=${!1}
  PACKAGE_NAME=$2
  ISA=$3
  DIRECTORY_NAME=$4
  # change package_name in RiscvTests.scala
  sed -e "s/{package}/$PACKAGE_NAME/" $WORK_DIR/src/test/resources/RiscvTests.scala > $WORK_DIR/src/test/scala/RiscvTests.scala
  for INST in ${INSTS[@]}
  do
    echo $INST
    # change package_name and hex_file in Memory.scala
    sed -e "s/{package}/$PACKAGE_NAME/" -e "s/{isa}/$ISA/" -e "s/{inst}/$INST/" $WORK_DIR/src/main/resources/Memory.scala > $WORK_DIR/src/main/scala/$DIRECTORY_NAME/Memory.scala
    # run sbt
    sbt "testOnly $PACKAGE_NAME.RiscvTest" > $RESULT_DIR/$INST.txt
  done
}

PACKAGE_NAME=$1
DIRECTORY_NAME=$2
loop_test UI_INSTS[@] $PACKAGE_NAME "ui" $DIRECTORY_NAME
loop_test UI_INSTS[@] $PACKAGE_NAME "mi" $DIRECTORY_NAME
.class public Test
.super java/lang/Object


.method public <init>()V
	aload_0
	invokespecial java/lang/Object/<init>()V
	return
.end method

.method public static main([Ljava/lang/String;)V
	.limit stack 0
	.limit locals 1
	return
.end method

.method public foo()I
	.limit stack 6
	.limit locals 4
	ldc 1
	istore_1
	ldc 2
	istore_2
	iload_1
	iload_2
	iadd
	istore_3
	iload_3
	ireturn
.end method
package org.mism.modelgen;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.mdkt.compiler.InMemoryJavaCompiler;
import org.mism.command.Command;
import org.mism.command.CommandFactory;
import org.mism.command.templates.ModifyCommandTemplate;
import org.mism.modelgen.api.Clonable;
import org.mism.modelgen.api.Mutable;
import org.mism.modelgen.api.ObservableList;
import org.mism.modelgen.ifaces.AbstractInterface;
import org.mism.modelgen.ifaces.Branch;
import org.mism.modelgen.ifaces.ChildInterface;
import org.mism.modelgen.ifaces.ClassNode;
import org.mism.modelgen.ifaces.DoubleContainmentPerson;
import org.mism.modelgen.ifaces.ExtendingInterface;
import org.mism.modelgen.ifaces.MethodNode;
import org.mism.modelgen.ifaces.OtherTestInterface;
import org.mism.modelgen.ifaces.ParentInterface;
import org.mism.modelgen.ifaces.SomeTypeWithListProperty;
import org.mism.modelgen.ifaces.TestInterface;
import org.mism.modelgen.ifaces.TestInterface2;
import org.mism.modelgen.ifaces.TreeSegment;

public class ModelGeneratorTest extends ModelGenerator {

	static class LocalAssertion {
		boolean ok;
		boolean triggered;

		void check() {
			assertTrue("test failed", ok);
			assertTrue("test not executed", triggered);
		}
	}

	@Test
	public void testListProperty() throws Exception {
		SomeTypeWithListProperty type = classify(SomeTypeWithListProperty.class);
		assertNotNull(type);

		final LocalAssertion test = new LocalAssertion();
		((Mutable) type).getChangeSupport().addPropertyChangeListener(
				"Numbers", evt -> {
					test.ok = ((int) evt.getNewValue() == 5);
					test.triggered = true;
				});
		Method m = type.getClass().getMethod("getNumbersRef");
		ObservableList<Integer> list = (ObservableList<Integer>) m.invoke(type);
		list.add(5);
		test.check();

		assertEquals(1, type.getNumbers().size());
		try {
			type.getNumbers().add(1);
			fail("List should be immutable");
		} catch (Exception e) {
			assertTrue(e instanceof UnsupportedOperationException);
		}
	}

	@Test
	public void testSingleContainment() throws Exception {
		ClassNode node = classify(ClassNode.class, MethodNode.class);

		assertNotNull(node);
	}

	@Test
	public void testToStringSimple() throws Exception {

		ExtendingInterface test = classify(ExtendingInterface.class,
				AbstractInterface.class, TestInterface.class);
		assertEquals(
				"{ \"@Type\":\"ExtendingInterface\", \"Hadoodle\": \"null\", \"SomeAbstractStuff\": \"null\", \"Name\": \"null\", \"ID\": \"null\", \"Other\": \"null\"}",
				test.toString());
	}

	@Test
	public void testToStringList() throws Exception {

		ParentInterface test = classify(ParentInterface.class,
				ChildInterface.class);
		assertEquals("{ \"@Type\":\"ParentInterface\", \"Children\":[]}",
				test.toString());
	}

	@Test
	public void testGenerateExtendingInterface() throws Exception {
		ExtendingInterface ext = classify(ExtendingInterface.class,
				TestInterface.class, OtherTestInterface.class,
				AbstractInterface.class);

		assertNotNull(ext);
	}

	@Test
	public void testContainment() throws Exception {
		ParentInterface parent = classify(ParentInterface.class,
				ChildInterface.class);

		assertNotNull(parent);
	}

	@Test
	public void testVisitor() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		Model model = new Model();
		model.init(TreeSegment.class, Branch.class);

		InMemoryJavaCompiler comp = new InMemoryJavaCompiler();

		Type t = model.resolve(TreeSegment.class);
		StringWriter tout = new StringWriter();
		generator.generateType(tout, t);
		comp.addSource(t.getJavaFQN(), tout.toString());

		Type ot = model.resolve(Branch.class);

		StringWriter otout = new StringWriter();
		generator.generateType(otout, ot);
		comp.addSource(ot.getJavaFQN(), otout.toString());

		StringWriter out = new StringWriter();
		generator.generateVisitor(out, model);

		comp.addSource("org.mism.modelgen.ifaces.ModelVisitor", out.toString());
		System.out.println(out);

		Map<String, Class<?>> clzzes = comp.compileAll();
		Class<?> modelVisitor = clzzes
				.get("org.mism.modelgen.ifaces.ModelVisitor");
		assertNotNull(modelVisitor);
	}

	@Test
	public void testGenerateClass() throws Exception {

		ModelGenerator generator = new ModelGenerator();
		StringWriter out = new StringWriter();
		Model model = new Model();
		model.init(TestInterface.class, OtherTestInterface.class);
		Type t = model.resolve(TestInterface.class);
		generator.generateType(out, t);

		System.out.println(out);

		Class<?> clazz = InMemoryJavaCompiler.compile(t.getJavaFQN(),
				out.toString());
		TestInterface test = (TestInterface) clazz.newInstance();

		assertEquals(1, t.getRequired().length);
		assertEquals(1, t.getRequiredCount());
		assertNotNull(clazz.getConstructor(String.class));

		assertNotNull(test);

	}

	@Test
	public void testShallowCloning() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		StringWriter out = new StringWriter();
		Model model = new Model();
		model.init(TestInterface.class, OtherTestInterface.class);
		Type t = model.resolve(TestInterface.class);
		generator.generateType(out, t);

		Class<?> clazz = InMemoryJavaCompiler.compile(t.getJavaFQN(),
				out.toString());
		TestInterface test = (TestInterface) clazz.newInstance();

		out = new StringWriter();
		generator.generateType(out, model.resolve(OtherTestInterface.class));
		System.out.println(out);
		Class<?> otherClazz = InMemoryJavaCompiler.compile(
				model.resolve(OtherTestInterface.class).getJavaFQN(),
				out.toString());

		OtherTestInterface other = (OtherTestInterface) otherClazz
				.newInstance();

		test.getClass().getMethod("setName", String.class).invoke(test, "test");
		test.getClass().getMethod("setID", BigInteger.class)
				.invoke(test, BigInteger.valueOf(37));
		test.getClass().getMethod("setOther", OtherTestInterface.class)
				.invoke(test, other);

		TestInterface cloned = (TestInterface) ((Clonable<?>) test)
				.shallowClone();
		assertEquals(cloned.getID(), test.getID());
		assertEquals(cloned.getName(), test.getName());
		assertNotSame(cloned, test);
		assertSame(cloned.getOther(), other);
	}

	@Test
	public void testDeepCloning() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		StringWriter out = new StringWriter();
		Model model = new Model();
		model.init(TestInterface.class, OtherTestInterface.class);
		Type t = model.resolve(TestInterface.class);
		generator.generateType(out, t);

		Class<?> clazz = InMemoryJavaCompiler.compile(t.getJavaFQN(),
				out.toString());
		TestInterface test = (TestInterface) clazz.newInstance();

		out = new StringWriter();
		generator.generateType(out, model.resolve(OtherTestInterface.class));
		System.out.println(out);
		Class<?> otherClazz = InMemoryJavaCompiler.compile(
				model.resolve(OtherTestInterface.class).getJavaFQN(),
				out.toString());

		OtherTestInterface other = (OtherTestInterface) otherClazz
				.newInstance();

		test.getClass().getMethod("setName", String.class).invoke(test, "test");
		test.getClass().getMethod("setID", BigInteger.class)
				.invoke(test, BigInteger.valueOf(37));
		test.getClass().getMethod("setOther", OtherTestInterface.class)
				.invoke(test, other);

		TestInterface cloned = (TestInterface) ((Clonable<?>) test).deepClone();
		assertEquals(cloned.getID(), test.getID());
		assertEquals(cloned.getName(), test.getName());
		assertNotSame(cloned, test);
		assertNotSame(cloned.getOther(), other);
	}

	@Test
	public void testEquals() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		StringWriter out = new StringWriter();
		Model model = new Model();
		model.init(TestInterface.class, OtherTestInterface.class);
		Type t = model.resolve(TestInterface.class);
		generator.generateType(out, t);

		System.out.println(out);

		Class<?> clazz = InMemoryJavaCompiler.compile(t.getJavaFQN(),
				out.toString());
		TestInterface test1 = (TestInterface) clazz.newInstance();
		TestInterface test2 = (TestInterface) clazz.newInstance();
		test1.getClass().getMethod("setName", String.class)
				.invoke(test1, "test");
		test1.getClass().getMethod("setID", BigInteger.class)
				.invoke(test1, BigInteger.valueOf(37));

		test1.getClass().getMethod("setName", String.class)
				.invoke(test2, "test");
		test1.getClass().getMethod("setID", BigInteger.class)
				.invoke(test2, BigInteger.valueOf(37));

		assertEquals(test1, test2);
		assertEquals(test2, test1);

		test1.getClass().getMethod("setID", BigInteger.class)
				.invoke(test2, BigInteger.valueOf(39));

		assertNotEquals(test1, test2);
	}

	@Test
	public void testHashCode() throws Exception {

		TestInterface test1 = classify(TestInterface.class);
		test1.getClass().getMethod("setName", String.class)
				.invoke(test1, "test");
		test1.getClass().getMethod("setID", BigInteger.class)
				.invoke(test1, BigInteger.valueOf(37));

		assertNotEquals(test1.hashCode(), System.identityHashCode(test1));
		TestInterface clone = (TestInterface) ((Clonable<?>) test1)
				.shallowClone();
		assertEquals(test1.hashCode(), clone.hashCode());

		test1.getClass().getMethod("setID", BigInteger.class)
				.invoke(test1, BigInteger.valueOf(39));

		assertNotEquals(test1, clone);
	}

	@Test
	public void testGenerateFactory() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		StringWriter out = new StringWriter();
		Model model = new Model();
		model.init(TestInterface.class, OtherTestInterface.class);
		InMemoryJavaCompiler comp = new InMemoryJavaCompiler();

		Type t = model.resolve(TestInterface.class);
		StringWriter tout = new StringWriter();
		generator.generateType(tout, t);
		comp.addSource(t.getJavaFQN(), tout.toString());

		Type ot = model.resolve(OtherTestInterface.class);
		StringWriter otout = new StringWriter();
		generator.generateType(otout, ot);
		comp.addSource(ot.getJavaFQN(), otout.toString());

		generator.generateFactory(out, model);
		System.out.println(out);

		comp.addSource("org.mism.modelgen.ifaces.ModelFactory", out.toString());
		Class<?> clzz = comp.compileAll().get(
				"org.mism.modelgen.ifaces.ModelFactory");

		Object o = clzz.getEnumConstants()[0];
		TestInterface test = (TestInterface) clzz.getMethod(
				"createTestInterface", String.class).invoke(o, "TEST");

		assertEquals("TEST", test.getName());

	}

	@Test
	public void testGenerateFactoryForContainment() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		StringWriter out = new StringWriter();
		Model model = new Model();
		model.init(ParentInterface.class, ChildInterface.class);
		InMemoryJavaCompiler comp = new InMemoryJavaCompiler();

		Type t = model.resolve(ParentInterface.class);
		StringWriter tout = new StringWriter();
		generator.generateType(tout, t);
		comp.addSource(t.getJavaFQN(), tout.toString());

		Type ot = model.resolve(ChildInterface.class);
		StringWriter otout = new StringWriter();
		generator.generateType(otout, ot);
		comp.addSource(ot.getJavaFQN(), otout.toString());

		generator.generateFactory(out, model);
		System.out.println(out);

		comp.addSource("org.mism.modelgen.ifaces.ModelFactory", out.toString());
		Class<?> clzz = comp.compileAll().get(
				"org.mism.modelgen.ifaces.ModelFactory");

		Object factory = clzz.getEnumConstants()[0];
		ParentInterface p = (ParentInterface) clzz.getMethod(
				"createParentInterface").invoke(factory);

		assertNotNull(p);

	}

	@Test
	public void testGenerateCommands() throws Exception {
		ModelGenerator generator = new ModelGenerator();

		Model model = new Model();
		model.init(ParentInterface.class, ChildInterface.class);
		InMemoryJavaCompiler comp = new InMemoryJavaCompiler();

		Type t = model.resolve(ParentInterface.class);
		StringWriter tout = new StringWriter();
		generator.generateType(tout, t);
		comp.addSource(t.getJavaFQN(), tout.toString());

		Type ot = model.resolve(ChildInterface.class);
		StringWriter otout = new StringWriter();
		generator.generateType(otout, ot);
		comp.addSource(ot.getJavaFQN(), otout.toString());

		StringWriter out = new StringWriter();
		generator.generateCommands(out, model);
		System.out.println(out);

		comp.addSource("org.mism.modelgen.ifaces.ModelCommandFactory",
				out.toString());
		Map<String, Class<?>> compiled = comp.compileAll();
		Class<?> clzz = compiled
				.get("org.mism.modelgen.ifaces.ModelCommandFactory");
		assertNotNull(clzz);

		CommandFactory modelCommandFactory = (CommandFactory) clzz
				.newInstance();

		ChildInterface child = (ChildInterface) compiled.get(ot.getJavaFQN())
				.newInstance();
		List<Command<ChildInterface>> cmds = modelCommandFactory
				.prepareCommands(ChildInterface.class, child);

		assertEquals(1, cmds.size());
		ModifyCommandTemplate<ChildInterface, String> command = (ModifyCommandTemplate) cmds
				.get(0);
		command.setNewValue("Test");
		command.execute();
		assertEquals("Test", child.getName());
		command.rollback();
		assertNull(child.getName());

	}

	@Test
	public void testSeveralRequiredProps() throws Exception {

		TestInterface2 test = (TestInterface2) classify(TestInterface2.class);

		assertNotNull(test);
		assertNotNull(test.getClass()
				.getConstructor(String.class, String.class));
	}

	protected static <T> T classify(Class<T> clzz, Class<?>... others)
			throws Exception {
		ModelGenerator generator = new ModelGenerator();
		Model model = new Model();

		InMemoryJavaCompiler compiler = new InMemoryJavaCompiler();
		if (others != null && others.length != 0) {
			Class<?>[] classes = new Class<?>[others.length + 1];
			classes[0] = clzz;
			System.arraycopy(others, 0, classes, 1, others.length);
			model.init(classes);
			for (Class<?> cl : classes) {
				Type t = model.resolve(cl);
				StringWriter out = new StringWriter();
				generator.generateType(out, t);
				compiler.addSource(t.getJavaFQN(), out.toString());
				System.out.println(out.toString());
			}
		} else {
			model.init(clzz);
			Type t = model.resolve(clzz);
			StringWriter out = new StringWriter();
			generator.generateType(out, t);
			compiler.addSource(t.getJavaFQN(), out.toString());
			System.out.println(out.toString());
		}
		Map<String, Class<?>> clazzes = compiler.compileAll();

		Class<?> clazz = clazzes.get(model.resolve(clzz).getJavaFQN());

		return clzz.cast(clazz.newInstance());
	}

	@Test
	public void testDoubleContainments() throws Exception {
		DoubleContainmentPerson p = classify(DoubleContainmentPerson.class);

		assertNotNull(p);

		// fail("Tests for proper handling of multiple containments missing!");
	}

	@Test
	public void testGenerateCode() throws Exception {
		ModelGenerator generator = new ModelGenerator();
		Path tempDir = Files.createTempDirectory("testGenerateCode");
		FileResourceSet fileSet = new FileResourceSet(tempDir.toFile());
		generator.generate(fileSet, TestInterface.class,
				OtherTestInterface.class);

		String[] paths = new String[] {
				"./org/mism/modelgen/ifaces/ModelCommandFactory.java",
				"./org/mism/modelgen/ifaces/ModelFactory.java",
				"./org/mism/modelgen/ifaces/ModelVisitor.java",
				"./org/mism/modelgen/ifaces/OtherTestInterfaceObject.java",
				"./org/mism/modelgen/ifaces/TestInterfaceObject.java" };

		for (String path : paths) {
			File f = new File(tempDir.toFile(), path);
			assertTrue(f.exists());
			f.delete();
		}

	}
}

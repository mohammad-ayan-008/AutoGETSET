package org.example;

import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

//@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("org.example.GetterSetter")
public class BuilderProcessor extends AbstractProcessor {
    private String pascal(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = this.processingEnv.getFiler();
        Messager messager = this.processingEnv.getMessager();

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(GetterSetter.class);

        for (Element element : elements) {
            if (!(element instanceof TypeElement typeElement)) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Builder  should only be placeable on classes.",
                        element
                );
                continue;

            }

            List<? extends Element> members = processingEnv.getElementUtils().getAllMembers(typeElement);
            List<VariableElement> fields = ElementFilter.fieldsIn(members).stream()
                    .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
                    .collect(Collectors.toList());

            for (VariableElement field : fields) {
                Set<Modifier> modifiers = field.getModifiers();
                if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.FINAL)) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Builder are not allowed to have any private or final non-static fields",
                            field
                    );
                    return true;
                }
            }

            String className = typeElement.getSimpleName().toString();
            Element enclosingElement = typeElement.getEnclosingElement();

            if (!(enclosingElement instanceof PackageElement packageElement)) {
                messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Builder must be top-level classes, not nested within another class",
                        element
                );
                return true;
            }

            String packageName = packageElement.isUnnamed() ? "" : packageElement.getQualifiedName().toString();
            String selfMethod = """
                    private %s self() {
                        return (%s) this;
                    }
                    """.formatted(className, className);

            BiFunction<String, String, String> methodDefinition = (fieldType, fieldName) -> {
                String pascalName = pascal(fieldName);
                return """
                        /**
                         * Get the current value for %s.
                         */
                        public %s get%s() {
                            return self().%s;
                        }

                        /**
                         * Set the current value for %s.
                         */
                        public void set%s(%s %s) {
                            self().%s = %s;
                        }
                        """.formatted(
                        fieldName,
                        fieldType, pascalName, fieldName,
                        fieldName,
                        pascalName, fieldType, fieldName,
                        fieldName, fieldName
                );
            };
            GetterSetter annotation = typeElement.getAnnotation(GetterSetter.class);
            String extendClass;
            try {
                extendClass = annotation.extend().toString();
            } catch (MirroredTypeException e) {
                extendClass = e.getTypeMirror().toString();
            }

            String packageDecl = packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";
            String classDeclStart = "sealed abstract class %sGS extends %s permits %s {\n\n"
                    .formatted(className,extendClass, className);
            String classDeclEnd = "}";

            StringBuilder classDecl = new StringBuilder();
            classDecl.append(packageDecl);
            classDecl.append(classDeclStart);
            classDecl.append(selfMethod);

            for (VariableElement field : fields) {
                classDecl.append(methodDefinition.apply(field.asType().toString(), field.getSimpleName().toString()));
            }

            classDecl.append(classDeclEnd);

            try {
                JavaFileObject file = filer.createSourceFile(packageName + "." + className + "GS", element);
                try (var writer = file.openWriter()) {
                    writer.append(classDecl.toString());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return true;
    }


}




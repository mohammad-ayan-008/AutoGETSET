package org.mainframe;


import com.google.auto.service.AutoService;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedAnnotationTypes("org.example.Generate")
public class GetterSetterProcessor extends AbstractProcessor {


    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Filer filer = processingEnv.getFiler();
        Messager messager = processingEnv.getMessager();

        Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(Generate.class);
        for (Element element:elementsAnnotatedWith) {
            if (!(element instanceof TypeElement typeElement)){
                messager.printMessage(Diagnostic.Kind.ERROR,"GetterSetter annotation is only for Classes");
                continue;
            }
            List<? extends Element> allMembers = processingEnv.getElementUtils().getAllMembers(typeElement);
            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,allMembers.toString());
            List<VariableElement> collect = ElementFilter.fieldsIn(allMembers).stream().
                    filter(x -> !x.getModifiers().contains(Modifier.STATIC))
                    .collect(Collectors.toList());

            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,collect.toString());
            for (VariableElement field : collect) {
                Set<Modifier> modifiers = field.getModifiers();
                if (modifiers.contains(Modifier.PRIVATE) || modifiers.contains(Modifier.FINAL)) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            "Builder are not allowed to have any private or final non-static fields",
                            field
                    );
                    return true;
                }

                String className = typeElement.getSimpleName().toString();
                Element enclosingElement = typeElement.getEnclosingElement();
               // processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,enclosingElement.getSimpleName().toString());
                if (!(enclosingElement instanceof PackageElement packageElement)){
                     messager.printMessage(Diagnostic.Kind.ERROR,"Builder must be top-level classes, not nested within another class");
                     return true;
                }
                String packageName = packageElement.isUnnamed() ? "":packageElement.getQualifiedName().toString();
                String selfMethod= """
                        private %s self(){
                           return (%s) this;
                        }
                        """.formatted(className,className);
                BiFunction<String,String,String> methodDef = (fieldType, fieldName)->{
                    String pascalName=pascal(fieldName);
                    return """
                           public %s get%s(){
                              return self().%s;
                           }
                           
                           public void set%s(%s %s){
                              self().%s=%s;
                           }
                           """.formatted(fieldType,pascalName,fieldName,pascalName,fieldType,fieldName,fieldName,fieldName);
                };
                Generate annotation = typeElement.getAnnotation(Generate.class);
                String extendingClass;
                try{
                    extendingClass = annotation.extend().toString();
                }catch (Exception e){
                    extendingClass = "java.lang.Object";
                    //processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,"Error");
                }

                String packageDel = packageName.isEmpty() ? "" : "package " +packageName +"; \n\n";
                String classStart = "sealed abstract class %sGS extends %s permits %s { \n\n".formatted(className,extendingClass,className);
                String classEnd ="}";
                StringBuilder classBuilder = new StringBuilder();
                classBuilder.append(packageDel);
                classBuilder.append(classStart);
                classBuilder.append(selfMethod);

                for(VariableElement f: collect){
                    classBuilder.append(methodDef.apply(f.asType().toString(),f.getSimpleName().toString()));
                }
                classBuilder.append(classEnd);
                try{
                    JavaFileObject file = filer.createSourceFile(packageName +"."+ className+"GS",element);
                    try(var writer = file.openWriter()){
                        writer.append(classBuilder.toString());
                    }
                }catch (Exception e){
                   processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,e.getLocalizedMessage());
                }

            }
        }
        return true;
    }
    private String pascal(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

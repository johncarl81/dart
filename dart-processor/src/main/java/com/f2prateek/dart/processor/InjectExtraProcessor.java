/*
 * Copyright 2013 Jake Wharton
 * Copyright 2014 Prateek Srivastava (@f2prateek)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.f2prateek.dart.processor;

import com.f2prateek.dart.common.AbstractDartProcessor;
import com.f2prateek.dart.common.InjectionTarget;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

public final class InjectExtraProcessor extends AbstractDartProcessor {

  @Override public boolean process(Set<? extends TypeElement> elements, RoundEnvironment env) {
    Map<TypeElement, InjectionTarget> targetClassMap = findAndParseTargets(env);

    for (Map.Entry<TypeElement, InjectionTarget> entry : targetClassMap.entrySet()) {
      TypeElement typeElement = entry.getKey();
      InjectionTarget injectionTarget = entry.getValue();

      Writer writer = null;
      // Generate the ExtraInjector
      try {
        ExtraInjectionGenerator extraInjectionGenerator =
            new ExtraInjectionGenerator(injectionTarget);
        JavaFileObject jfo = filer.createSourceFile(extraInjectionGenerator.getFqcn(), typeElement);
        if (isDebugEnabled) {
          System.out.println(
              "Extra Injector generated:\n" + extraInjectionGenerator.brewJava() + "---");
        }
        writer = jfo.openWriter();
        writer.write(extraInjectionGenerator.brewJava());
      } catch (IOException e) {
        error(typeElement, "Unable to write injector for type %s: %s", typeElement, e.getMessage());
      } finally {
        if (writer != null) {
          try {
            writer.close();
          } catch (IOException e) {
            error(typeElement, "Unable to close injector source file for type %s: %s", typeElement,
                e.getMessage());
          }
        }
      }
    }

    //return false here to let henson process the annotations too
    return false;
  }

  protected Map<TypeElement, InjectionTarget> findAndParseTargets(RoundEnvironment env) {
    Map<TypeElement, InjectionTarget> targetClassMap =
        new LinkedHashMap<TypeElement, InjectionTarget>();
    Set<TypeMirror> erasedTargetTypes = new LinkedHashSet<TypeMirror>();

    // Process each @InjectExtra elements.
    parseInjectExtraAnnotatedElements(env, targetClassMap, erasedTargetTypes);

    // Try to find a parent injector for each injector.
    for (Map.Entry<TypeElement, InjectionTarget> entry : targetClassMap.entrySet()) {
      String parentClassFqcn = findParentFqcn(entry.getKey(), erasedTargetTypes);
      if (parentClassFqcn != null) {
        entry.getValue().setParentTarget(parentClassFqcn);
      }
    }

    return targetClassMap;
  }
}

package org.robolectric.annotation.processing.validator;

import org.robolectric.annotation.processing.RobolectricModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Validator that checks usages of {@link org.robolectric.annotation.Implementation}.
 */
public class ImplementationValidator extends FoundOnImplementsValidator {

  private final Elements elementUtils;

  public ImplementationValidator(RobolectricModel model, ProcessingEnvironment env) {
    super(model, env, "org.robolectric.annotation.Implementation");

    this.elementUtils = env.getElementUtils();
  }

  @Override
  public Void visitExecutable(ExecutableElement elem, TypeElement parent) {
    // TODO: Check that it has the right signature

    model.documentMethod(parent, elem, elementUtils.getDocComment(elem));

    return null;
  }
}

package com.gentics.mesh.core.rest.node.field.impl;

import com.gentics.mesh.core.rest.node.field.NumberField;
import com.gentics.mesh.model.FieldTypes;

public class NumberFieldImpl implements NumberField {

	private String number;

	@Override
	public String getNumber() {
		return number;
	}

	@Override
	public void setNumber(String number) {
		this.number = number;
	}

	@Override
	public String getType() {
		return FieldTypes.NUMBER.toString();
	}

}

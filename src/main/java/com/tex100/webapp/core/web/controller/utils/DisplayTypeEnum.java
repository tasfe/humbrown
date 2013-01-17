package com.tex100.webapp.core.web.controller.utils;

import com.tex100.webapp.core.model.enumField.CoreEnumInterface;

public enum DisplayTypeEnum implements CoreEnumInterface<Integer> {
			SELECT (0)  , //select
			CHECK_BOX (1)  , //checkBox
			TEXT (2)  , //text
			DATE (3)  , //date
			HIDDEN (4)  , //hidden
			AUTO_COMPLETE(5) ;//autoComplete
		
	private int intValue;
	
	private DisplayTypeEnum(int intName){
		this.intValue=intName;
	}
	
	public String toString(){
		return String.valueOf(intValue);
	}

	public String getDescription() {
		return name().toLowerCase();
	}

	public Integer getValue() {
		return intValue;
	}
	
	public static DisplayTypeEnum getEnum(int intValue) {
		for (DisplayTypeEnum intEnum : DisplayTypeEnum .values()) {
			if (intEnum.getValue() == intValue) {
				return intEnum;
			}
		}
		return null;
	}
}

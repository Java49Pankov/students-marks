 package telran.students.model;

import java.util.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.*;
import telran.students.dto.*;

@Document(collection = "students")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class StudentDoc {
	@Id
	Long id;

	String name;

	@Setter
	String phone;

	List<Mark> marks;

	public void addMark(Mark mark) {
		marks.add(mark);
	}

	public static StudentDoc of(Student student) {
		StudentDoc studentDoc = new StudentDoc(student.id(), student.name(), student.phone(), null);
		studentDoc.marks = new ArrayList<>();
		return studentDoc;
	}

	private StudentDoc(Long id, String name, String phone, List<Mark> marks) {
		super();
		this.id = id;
		this.name = name;
		this.phone = phone;
		this.marks = marks;
	}

	public Student buildDto() {
		return new Student(id, name, phone);
	}
}

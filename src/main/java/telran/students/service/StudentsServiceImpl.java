package telran.students.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import telran.exceptions.NotFoundException;
import telran.students.dto.*;
import telran.students.model.StudentDoc;
import telran.students.repo.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class StudentsServiceImpl implements StudentsService {
	final StudentRepo studentRepo;

	@Override
	@Transactional
	public Student addStudent(Student student) {
		long id = student.id();
		if (studentRepo.existsById(id)) {
			throw new IllegalStateException(String.format("Student %d allredy exists", id));
		}
		studentRepo.save(StudentDoc.of(student));
		log.debug("saved {}", student);
		return student;
	}

	@Override
	@Transactional
	public Student updatePhone(long id, String phone) {
		StudentDoc studentDoc = getStudent(id);
		studentDoc.setPhone(phone);
		String oldPhone = studentDoc.getPhone();
		studentRepo.save(studentDoc);
		log.debug("Student {}, old phone number {}, new phone number {}", id, oldPhone, phone);
		return studentDoc.buildDto();
	}

	private StudentDoc getStudent(long id) {
		return studentRepo.findById(id)
				.orElseThrow(() -> new NotFoundException(String.format("Student %d not found", id)));
	}

	@Override
	@Transactional
	public List<Mark> addMark(long id, Mark mark) {
		StudentDoc studentDoc = getStudent(id);
		studentDoc.addMark(mark);
		studentRepo.save(studentDoc);
		log.debug("Student {} added mark {}", id, mark);
		return studentDoc.getMarks();
	}

	@Override
	@Transactional
	public Student removeStudent(long id) {
		StudentDoc studentDoc = studentRepo.findStudentNoMarks(id);
		if (studentDoc == null) {
			throw new NotFoundException(String.format("Student %d not found", id));
		}
		studentRepo.deleteById(id);
		log.debug("removed student {}, marks {}", studentDoc.getId(), studentDoc.getMarks());
		return studentDoc.buildDto();
	}

	@Override
	@Transactional(readOnly = true)
	public List<Mark> getMarks(long id) {
		StudentDoc studentDoc = studentRepo.findStudentMarks(id);
		if (studentDoc == null) {
			throw new NotFoundException(String.format("Student %d not found", id));
		}
		log.debug("student id {}, name {}, phone {}, marks{} ",
				studentDoc.getId(), studentDoc.getName(), studentDoc.getPhone(), studentDoc.getMarks());
		return studentDoc.getMarks();
	}

}

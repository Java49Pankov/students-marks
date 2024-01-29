package telran.students.service;

import java.util.*;

import telran.students.dto.*;

public interface StudentsService {
	Student addStudent(Student student);

	Student updatePhone(long id, String phone);

	List<Mark> addMark(long id, Mark mark);

	Student removeStudent(long id);

	List<Mark> getMarks(long id);

	Student getStudentByPhone(String phoneNumber);

	List<Student> getStudentsByPhonePrefix(String phonePrefix);

	List<Student> getStudentsAllMarsGoodMarks(int thresholdScore);

	List<Student> getStudentsFewMarks(int thresholdMarks);

	List<Student> getStudentsAllGoodMarksSubject(String subject, int thresholdScore);

	List<Student> getStudentsMarksAmountBetween(int min, int max);

	List<Mark> getStudentSubjectMarks(long id, String subject);
}

package com.cuibluetooth.bleeconomy.ui
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cuibluetooth.bleeconomy.databinding.ItemStudentRowBinding
import com.cuibluetooth.bleeconomy.model.Student


class StudentAdapter : ListAdapter<Student, StudentAdapter.StudentViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemStudentRowBinding.inflate(inflater, parent, false)
        return StudentViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StudentViewHolder(private val binding: ItemStudentRowBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(student: Student) {
            binding.studentCode.text =
                student.institutionalCode?.takeIf { it.isNotBlank() } ?: "—"
            binding.studentName.text =
                student.name?.takeIf { it.isNotBlank() } ?: student.username
            binding.studentProject.text =
                student.projectName?.takeIf { it.isNotBlank() } ?: "—"
            //TODO binding.studentCheckbox.isChecked = student.isTracked
            binding.root.setOnClickListener {
                // TODO Later add a popup to the respective Student
            }
        }

        companion object {
            private val DiffCallback = object : DiffUtil.ItemCallback<Student>() {
                override fun areItemsTheSame(oldItem: Student, newItem: Student): Boolean =
                    oldItem.id == newItem.id

                override fun areContentsTheSame(oldItem: Student, newItem: Student): Boolean =
                    oldItem == newItem
            }
        }
    }
}